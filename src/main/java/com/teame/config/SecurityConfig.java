package com.teame.config;

import com.teame.member.customUser.CustomUserDetailsService;
import com.teame.member.jwt.JWTFilter;
import com.teame.member.jwt.JWTUtil;
import com.teame.member.jwt.LoginFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final AuthenticationConfiguration authenticationConfiguration;
  private final JWTUtil jwtUtil;
  private final CustomUserDetailsService customUserDetailsService;

  // BCrypt 암호화 메서드
  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  public UserDetailsService userDetailsService() {
    return customUserDetailsService;
  }

  @Bean
  public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
    authProvider.setUserDetailsService(customUserDetailsService);
    authProvider.setPasswordEncoder(bCryptPasswordEncoder());
    return authProvider;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http.cors(httpSecurityCorsConfigurer -> {
      httpSecurityCorsConfigurer.configurationSource(corsConfigurationSource());
    });

    http.csrf(AbstractHttpConfigurer::disable);
    http.formLogin(AbstractHttpConfigurer::disable);
    http.httpBasic(AbstractHttpConfigurer::disable);

    http
        .authorizeHttpRequests((auth) -> auth
            .requestMatchers("/api/admin").hasRole("ADMIN")
            .requestMatchers("/**").permitAll()
            .anyRequest().authenticated()
        );

    http.addFilterBefore(
        new JWTFilter(jwtUtil),
        UsernamePasswordAuthenticationFilter.class);

    // 필터 체인에 LoginFilter와 JWTFilter를 추가
    http.addFilterAfter(
        new LoginFilter(
            authenticationManager(authenticationConfiguration), jwtUtil),
        UsernamePasswordAuthenticationFilter.class);

    // 세션 데이터 생성 X
    http.sessionManagement((session) -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    );

    http.authenticationProvider(authenticationProvider());

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {

    CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(Arrays.asList(
        "http://localhost:8080",
        "http://localhost:5173",
        "https://www.circlespace.site",
        "https://circlespace.site"
    ));
    configuration.setAllowedMethods(Arrays.asList("HEAD", "GET", "POST", "PUT", "DELETE"));
    configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
  }

}
