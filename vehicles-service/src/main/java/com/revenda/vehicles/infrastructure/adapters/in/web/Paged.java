package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.model.Paging;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A consulta de uma listagem paginada ({@code ?page=0&size=20}); cada uma declara os dois com estas regras. */
interface Paged {

    Integer page();

    Integer size();

    default Paging toPaging() {
        return Paging.of(page(), size());
    }

    /** O número da página: de 0 em diante. */
    @Min(0)
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface PageNumber {

        String message() default "";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** Itens por página: de 1 a {@link Paging#MAX_SIZE}. */
    @Min(1)
    @Max(Paging.MAX_SIZE)
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface PageSize {

        String message() default "";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }
}
