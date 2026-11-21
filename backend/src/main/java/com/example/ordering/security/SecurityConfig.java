package com.example.ordering.security;

import com.example.ordering.upload.UploadProperties;
import com.example.ordering.couple.CoupleAccess;
import com.example.ordering.user.UserAccount;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import com.example.ordering.push.PushProperties;
import com.example.ordering.weather.WeatherProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import jakarta.servlet.DispatcherType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({RateLimitConfig.class, ClientIpProperties.class, UploadProperties.class, PushProperties.class, WeatherProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TokenAuthenticationFilter tokenAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ObjectProvider<CoupleAccess> coupleAccess,
            @Value("${app.couple.offline-integrations:false}") boolean offlineIntegrations
    )
            throws Exception {
        CoupleAccess couple = coupleAccess.getIfAvailable(CoupleAccess::legacy);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> {
                    exception.authenticationEntryPoint(authenticationEntryPoint);
                    if (couple.enabled()) exception.accessDeniedHandler(authenticationEntryPoint);
                })
                // Public HTTPS responses are normalized by Nginx so clients receive one
                // consistent copy of each security header instead of conflicting values.
                .headers(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                            .requestMatchers("/uploads/friends/**").denyAll();
                    if (!couple.enabled()) {
                        auth.requestMatchers("/app-version.json", "/images/**", "/uploads/**").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                                .requestMatchers("/api/admin/users/**", "/api/admin/orders/**").hasRole("SUPER_ADMIN")
                                .requestMatchers("/api/admin/shop-orders/**", "/api/admin/menu-items/**",
                                        "/api/admin/shop-items/**", "/api/uploads/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                                .anyRequest().authenticated();
                        return;
                    }
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                            .requestMatchers(HttpMethod.GET, "/app-version.json", "/images/**").permitAll()
                            .requestMatchers("/api/auth/register", "/api/admin/**", "/api/friends/search",
                                    "/api/friends/requests/**").denyAll();
                    if (offlineIntegrations) {
                        auth.requestMatchers("/api/ai/**", "/api/weather/**").denyAll();
                    }
                    if (couple.test()) {
                        auth.requestMatchers("/api/test/**").access((authentication, request) ->
                                        new AuthorizationDecision(authentication.get().getPrincipal() instanceof UserAccount user
                                                && couple.isDeveloper(user)))
                                .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated();
                    } else {
                        auth.requestMatchers("/api/test/**").denyAll()
                                .requestMatchers(HttpMethod.GET, "/uploads/app-version.json",
                                        "/uploads/apk/qingcheng-ordering-release.apk").permitAll();
                    }
                    auth.anyRequest().hasAuthority("COUPLE_MEMBER");
                })
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, TokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TokenAuthenticationFilter> tokenAuthenticationFilterRegistration(TokenAuthenticationFilter filter) {
        FilterRegistrationBean<TokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
