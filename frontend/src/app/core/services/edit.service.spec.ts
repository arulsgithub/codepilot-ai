import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { EditService } from './edit.service';
import {
  ApplyEditsRequest,
  ApplyEditsResponse,
  EditPlanResponse,
} from '../models/edit.model';

describe('EditService', () => {
  let service: EditService;
  let httpMock: HttpTestingController;

  const planResponse: EditPlanResponse = {
    summary: 'Add null guard to ChatService.chat',
    applicable: true,
    previews: [
      {
        relativeFilePath: 'backend/src/main/java/com/codepilot/chat/service/ChatService.java',
        valid: true,
        problem: null,
        unifiedDiff: '--- a/ChatService.java\n+++ b/ChatService.java\n@@ -12,6 +12,9 @@\n ctx\n-old\n+new\n',
        searchText: 'old',
        replaceText: 'new',
        lastModifiedMs: 1757612345678,
      },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EditService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('POSTs repositoryRoot + instruction to /api/v1/edits/plan', () => {
    let result: EditPlanResponse | undefined;
    service.planEdits('E:\\repo', 'add null-checking to ChatService.chat').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/v1/edits/plan');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      repositoryRoot: 'E:\\repo',
      instruction: 'add null-checking to ChatService.chat',
    });
    req.flush(planResponse);

    expect(result).toEqual(planResponse);
  });

  it('POSTs the approved edits verbatim to /api/v1/edits/apply', () => {
    const request: ApplyEditsRequest = {
      repositoryRoot: 'E:\\repo',
      edits: [
        {
          relativeFilePath: 'ChatService.java',
          searchText: 'old',
          replaceText: 'new',
          expectedLastModifiedMs: 1757612345678,
        },
      ],
    };
    const success: ApplyEditsResponse = {
      applied: true,
      message: 'Applied edits to 1 file(s); 3 chunk(s) re-indexed',
      changedFiles: ['ChatService.java'],
      backupLocation: 'E:\\repo\\.codepilot-backups\\20260910-143022',
      problems: [],
    };

    let result: ApplyEditsResponse | undefined;
    service.applyEdits(request).subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/v1/edits/apply');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(success);

    expect(result).toEqual(success);
  });

  it('surfaces a 409 conflict as an HttpErrorResponse carrying the problems body', () => {
    const conflictBody: ApplyEditsResponse = {
      applied: false,
      message: 'Edits were not applied',
      changedFiles: [],
      backupLocation: null,
      problems: ['ChatService.java: file changed on disk since the edit was planned - re-plan the edit'],
    };

    let error: HttpErrorResponse | undefined;
    service
      .applyEdits({ repositoryRoot: 'E:\\repo', edits: [] })
      .subscribe({ error: (e: HttpErrorResponse) => (error = e) });

    httpMock.expectOne('/api/v1/edits/apply').flush(conflictBody, {
      status: 409,
      statusText: 'Conflict',
    });

    expect(error?.status).toBe(409);
    expect((error?.error as ApplyEditsResponse).problems).toEqual(conflictBody.problems);
  });
});
