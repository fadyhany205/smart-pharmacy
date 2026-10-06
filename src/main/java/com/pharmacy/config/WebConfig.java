package com.pharmacy.config;

import com.pharmacy.service.SettingsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    /** Language is kept in a cookie; when no cookie exists the default from Settings is used (Arabic). */
    @Bean
    public LocaleResolver localeResolver(SettingsService settings) {
        CookieLocaleResolver resolver = new CookieLocaleResolver("lang");
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setDefaultLocaleFunction(request -> {
            try {
                return Locale.forLanguageTag(settings.get().getDefaultLanguage());
            } catch (Exception e) {
                return Locale.forLanguageTag("ar");
            }
        });
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        registry.addInterceptor(interceptor);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(props.uploadDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
