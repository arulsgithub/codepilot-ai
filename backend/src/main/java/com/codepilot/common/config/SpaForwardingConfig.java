package com.codepilot.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built Angular app and falls back to index.html for unknown paths.
 *
 * Angular routes client-side: /chat/abc-123 is a route the browser resolves, not a file. A
 * visitor who refreshes on that URL asks the SERVER for it, and without this they get a 404
 * on a page that works perfectly when navigated to from inside the app. Classic SPA
 * deployment bug, and it only shows up in production because `ng serve` handles it for you.
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

    /**
     * Resolved once. In local development there is no built frontend on the classpath at all
     * (the Angular dev server serves it), so every fallback must be skipped rather than
     * handing back a Resource that does not exist.
     */
    private static final Resource INDEX = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {

                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // Not a real file. Anything under api/, actuator/, oauth2/ or login/
                        // is handled by a controller or filter - returning index.html for a
                        // miss there would mask genuine 404s behind an HTML page, and a client
                        // parsing that HTML as JSON fails far from the actual mistake.
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("actuator/")
                                || resourcePath.startsWith("oauth2/")
                                || resourcePath.startsWith("login/")) {
                            return null;
                        }

                        // MUST check existence before returning it. Spring calls lastModified()
                        // on whatever comes back, and a non-existent ClassPathResource throws
                        // FileNotFoundException there - surfacing as a 500 on something as
                        // innocuous as /favicon.ico, which is what happened locally where
                        // /static/ is empty.
                        if (!INDEX.exists()) {
                            return null;
                        }

                        // Everything else is an Angular route: hand back the app and let it
                        // route client-side.
                        return INDEX;
                    }
                });
    }
}