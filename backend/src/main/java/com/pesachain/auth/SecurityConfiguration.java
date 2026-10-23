package com.pesachain.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfRepository,
            AuthService authService,
            ObjectMapper objectMapper) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .securityContext(context -> context
                        .requireExplicitSave(true)
                        .securityContextRepository(contextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .addFilterAfter(new CurrentAccountFilter(authService, contextRepository), org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMINISTRATOR")
                        .requestMatchers("/api/v1/officer/**").hasRole("LOAN_OFFICER")
                        .requestMatchers("/api/v1/loans").hasRole("LOAN_OFFICER")
                        .requestMatchers("/api/v1/loans/**").hasRole("BORROWER")
                        .requestMatchers("/api/v1/borrowers/**").hasRole("BORROWER")
                        .requestMatchers("/api/v1/applications/**").hasRole("BORROWER")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                response, objectMapper, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                                "Authentication is required.", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                response, objectMapper, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                                "You do not have permission to perform this action.", request.getRequestURI())))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }

    private static void writeProblem(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String detail,
            String path) throws IOException {
        var problem = org.springframework.http.ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://pesachain.local/problems/" + code.toLowerCase()));
        problem.setProperty("code", code);
        problem.setProperty("path", path);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
