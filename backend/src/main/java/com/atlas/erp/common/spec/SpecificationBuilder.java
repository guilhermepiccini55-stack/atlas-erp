package com.atlas.erp.common.spec;

import com.atlas.erp.common.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

/**
 * Monta filtros dinâmicos (JPA Specification) a partir de parâmetros opcionais:
 * parâmetros nulos ou em branco são ignorados.
 */
public final class SpecificationBuilder<T> {

    private final List<Specification<T>> specs = new ArrayList<>();

    private SpecificationBuilder() {
    }

    public static <T> SpecificationBuilder<T> of() {
        return new SpecificationBuilder<>();
    }

    /** Busca textual (contém, sem diferenciar maiúsculas) em qualquer um dos campos informados. */
    public SpecificationBuilder<T> search(String term, String... fields) {
        if (term == null || term.isBlank() || fields.length == 0) {
            return this;
        }
        String pattern = "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
        specs.add((root, query, cb) -> {
            Predicate[] predicates = new Predicate[fields.length];
            for (int i = 0; i < fields.length; i++) {
                predicates[i] = cb.like(cb.lower(root.get(fields[i]).as(String.class)), pattern, '\\');
            }
            return cb.or(predicates);
        });
        return this;
    }

    /** Igualdade exata (enums, números, UUIDs, booleanos...). Ignora valor nulo. */
    public SpecificationBuilder<T> equal(String field, Object value) {
        if (value == null) {
            return this;
        }
        specs.add((root, query, cb) -> cb.equal(root.get(field), value));
        return this;
    }

    /** Intervalo de datas [from, to) sobre um campo Instant. Ignora limites nulos. */
    public SpecificationBuilder<T> between(String field, Instant from, Instant to) {
        if (from != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get(field), from));
        }
        if (to != null) {
            specs.add((root, query, cb) -> cb.lessThan(root.<Instant>get(field), to));
        }
        return this;
    }

    /** Adiciona uma especificação arbitrária. */
    public SpecificationBuilder<T> and(Specification<T> spec) {
        if (spec != null) {
            specs.add(spec);
        }
        return this;
    }

    public Specification<T> build() {
        return Specification.allOf(specs);
    }

    /**
     * Garante que a ordenação use apenas campos permitidos (evita ordenar por campos internos como senha)
     * e aplica {@code fallback} quando nenhuma ordenação foi pedida.
     */
    public static Pageable safePageable(Pageable pageable, Set<String> allowedSortFields, Sort fallback) {
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!allowedSortFields.contains(order.getProperty())) {
                throw new BusinessException(
                        "Ordenação inválida: '" + order.getProperty() + "'. Campos permitidos: "
                                + String.join(", ", allowedSortFields.stream().sorted().toList()) + ".",
                        HttpStatus.BAD_REQUEST);
            }
        }
        if (sort.isUnsorted()) {
            sort = fallback;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
