package com.revenda.errorprobe;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.ErrorCode;
import com.revenda.common.error.ErrorKind;
import com.revenda.common.security.Route;
import com.revenda.common.security.SecurityRoutes;
import jakarta.servlet.Filter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
class ErrorProbeApplication {

    static final String LEAKED_DETAIL = "detalhe interno 123.456.789-09";

    @Getter
    @RequiredArgsConstructor
    enum ProbeError implements ErrorCode {
        VEHICLE_UNAVAILABLE(ErrorKind.CONFLICT,
                "Este veículo acabou de ser reservado por outra pessoa. Escolha outro ou volte mais tarde.");

        private final ErrorKind kind;
        private final String message;
    }

    record VehicleRequest(@NotBlank String brand, @Positive long priceCents, @Min(1900) int year) {
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @PostMapping("/vehicles/{id}/reservations")
        void reserve(@PathVariable long id) {
            throw BusinessException.of(ProbeError.VEHICLE_UNAVAILABLE);
        }

        @GetMapping("/vehicles/{id}")
        void detail(@PathVariable long id) {
            throw BusinessException.notFound();
        }

        @PostMapping("/plates")
        void plate() {
            throw BusinessException.invalidField("plate", "PLATE_INVALID", "Placa inválida.");
        }

        @PostMapping("/vehicles")
        VehicleRequest create(@Valid @RequestBody VehicleRequest body) {
            return body;
        }

        @GetMapping("/vehicles")
        String list(@RequestParam("size") @Max(100) int size) {
            return "ok";
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException(LEAKED_DETAIL);
        }
    }

    @Bean
    SecurityRoutes probeRoutes() {
        return () -> List.of(Route.open("/probe/**"));
    }

    @Bean
    FilterRegistrationBean<Filter> failingFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
            throw new IllegalStateException(LEAKED_DETAIL);
        });
        registration.addUrlPatterns("/probe/filter-failure");
        return registration;
    }
}
