package com.codepilot.edit.service;

import com.codepilot.ai.dto.LLMMessage;
import com.codepilot.ai.dto.LLMRequest;
import com.codepilot.ai.dto.LLMResponse;
import com.codepilot.ai.model.ModelMode;
import com.codepilot.ai.model.ModelRouter;
import com.codepilot.ai.model.ResolvedModel;
import com.codepilot.ai.prompt.PromptBuilder;
import com.codepilot.edit.dto.EditPlan;
import com.codepilot.edit.dto.EditPlanResponse;
import com.codepilot.edit.dto.FileEdit;
import com.codepilot.retrieval.dto.CallSite;
import com.codepilot.retrieval.dto.EnrichedContext;
import com.codepilot.retrieval.service.SymbolAwareRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class EditPlannerService {

    private static final Logger log = LoggerFactory.getLogger(EditPlannerService.class);

    /** Editing needs more surrounding context than Q&A - a method alone isn't enough to edit safely. */
    private static final int EDIT_CONTEXT_CHUNKS = 12;

    /** Cap on call-site chunks added on top. Each costs prompt budget; a popular utility has many. */
    private static final int MAX_CALL_SITE_CHUNKS = 8;

    private final SymbolAwareRetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final ModelRouter modelRouter;
    private final EditBlockParser parser;
    private final EditValidator validator;
    private final DiffService diffService;

    public EditPlannerService(SymbolAwareRetrievalService retrievalService,
                              PromptBuilder promptBuilder,
                              ModelRouter modelRouter,
                              EditBlockParser parser,
                              EditValidator validator,
                              DiffService diffService) {
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.modelRouter = modelRouter;
        this.parser = parser;
        this.validator = validator;
        this.diffService = diffService;
    }

    public EditPlanResponse planEdits(String repositoryRoot, String instruction) {

        // Semantic search finds the code to change; the symbol index adds the code that uses it.
        EnrichedContext context = retrievalService.retrieveWithCallSites(
                repositoryRoot, instruction, EDIT_CONTEXT_CHUNKS, MAX_CALL_SITE_CHUNKS);

        if (context.isEmpty()) {
            return new EditPlanResponse(
                    "No indexed code found for this repository - run indexing first.",
                    false, List.of());
        }

        // Editing is the hardest reasoning task we do, so it routes to REASONING, not CODE.
        // Temperature 0.0 (not 0.2): exact-match SEARCH text leaves no room for creativity.
        ResolvedModel resolved = modelRouter.resolve(ModelMode.REASONING);
        List<LLMMessage> messages = List.of(
                new LLMMessage("system",
                        promptBuilder.buildEditSystemPrompt(context.allChunks(), context.callSites())),
                new LLMMessage("user", instruction));

        LLMResponse response = resolved.client().chat(
                new LLMRequest(resolved.model(), messages, 0.0, false));

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new IllegalStateException("AI provider returned an empty response");
        }

        String raw = response.choices().getFirst().message().content();
        EditPlan plan = parser.parse(raw);

        if (plan.edits().isEmpty()) {
            // Model declined or produced malformed output - surface its summary rather than
            // pretending we have a plan.
            log.warn("Edit planning produced no edits. Model said: {}", plan.summary());
            return new EditPlanResponse(
                    plan.summary().isBlank() ? "The model produced no applicable edits." : plan.summary(),
                    false, List.of());
        }

        List<EditPlanResponse.EditPreview> previews = new ArrayList<>();
        boolean allValid = true;

        for (FileEdit edit : plan.edits()) {
            String problem = validator.validate(repositoryRoot, edit);
            if (problem != null) {
                allValid = false;
                previews.add(new EditPlanResponse.EditPreview(
                        edit.relativeFilePath(), false, problem, null,
                        edit.searchText(), edit.replaceText(), 0L));
                continue;
            }
            try {
                Path target = Path.of(repositoryRoot).resolve(edit.relativeFilePath());
                String before = Files.readString(target);
                String after = validator.applyInMemory(before, edit);
                previews.add(new EditPlanResponse.EditPreview(
                        edit.relativeFilePath(), true, null,
                        diffService.unifiedDiff(edit.relativeFilePath(), before, after),
                        edit.searchText(), edit.replaceText(),
                        Files.getLastModifiedTime(target).toMillis()));
            } catch (IOException e) {
                allValid = false;
                previews.add(new EditPlanResponse.EditPreview(
                        edit.relativeFilePath(), false, "Could not read file: " + e.getMessage(),
                        null, edit.searchText(), edit.replaceText(), 0L));
            }
        }

        return new EditPlanResponse(
                withCallSiteNote(plan.summary(), context.callSites()), allValid, previews);
    }

    /**
     * Surfaces what reference analysis contributed, in the summary the UI already displays.
     * Without this the feature is invisible - the plan just silently gets better, and you have
     * no way to tell whether call sites were considered or simply not found.
     */
    private String withCallSiteNote(String summary, List<CallSite> callSites) {
        if (callSites.isEmpty()) {
            return summary;
        }
        Set<String> files = new LinkedHashSet<>();
        callSites.forEach(s -> files.add(s.relativeFilePath()));
        long confirmed = callSites.stream().filter(CallSite::confirmed).count();

        return summary + "  [Reference analysis found " + callSites.size() + " call site(s) in "
                + files.size() + " file(s), " + confirmed + " confirmed - all were shown to the model.]";
    }
}