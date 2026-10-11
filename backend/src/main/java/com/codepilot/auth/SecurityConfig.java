package com.codepilot.auth;

import com.codepilot.auth.service.GitHubOAuth2UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GitHubOAuth2UserService oAuth2UserService;
    private final String frontendUrl;

    public SecurityConfig(GitHubOAuth2UserService oAuth2UserService,
                          @Value("${codepilot.frontend-url:/}") String frontendUrl) {
        this.oAuth2UserService = oAuth2UserService;
        this.frontendUrl = frontendUrl;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // The Angular app itself must load before anyone can sign in.
                        .requestMatchers("/", "/index.html", "/*.js", "/*.css", "/*.ico",
                                "/assets/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Everything else is authenticated. Deny by default, so an endpoint added
                        // later is protected automatically rather than being forgotten.
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                        .defaultSuccessUrl(frontendUrl, true)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
                        .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(ex -> ex
                        // Without this, an unauthenticated XHR gets a 302 to GitHub, which the browser
                        // follows and then fails - producing a baffling console error. A plain 401 lets
                        // the frontend show a "Sign in" button instead.
                        //
                        // The matcher is a lambda rather than AntPathRequestMatcher: that class was
                        // removed in Spring Security 7, and RequestMatcher is a functional interface,
                        // so a lambda needs no import and survives future version bumps.
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/"))
                )
                // CSRF disabled: the API takes JSON from our own SPA and has no cookie-authenticated
                // form posts. A deliberate decision, not an oversight - revisit if form submissions
                // are ever added.
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}