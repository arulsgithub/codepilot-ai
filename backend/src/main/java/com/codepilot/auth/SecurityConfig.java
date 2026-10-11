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
                        // The API is the protected surface. Everything else is the Angular app, which
                        // MUST load for a signed-out visitor - otherwise they cannot reach the sign-in
                        // button, and the app is unusable to everyone who is not already logged in.
                        //
                        // Listing individual static patterns (/*.js, /assets/**) was fragile: a
                        // production build emits hashed names and nested paths, and anything missed
                        // 401s, leaving a blank page with no clue why.
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().permitAll()
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
                        // follows and then fails - a baffling console error. A plain 401 lets the
                        // frontend show its sign-in screen instead.
                        //
                        // A lambda rather than AntPathRequestMatcher: that class was removed in Spring
                        // Security 7, and RequestMatcher is a functional interface.
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/"))
                )
                // CSRF disabled: the API takes JSON from our own SPA and has no cookie-authenticated
                // form posts. A deliberate decision - revisit if form submissions are added.
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}