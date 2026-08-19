package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.ExportStance;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.model.SupplierTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * One query for every combination of filters, with a null meaning "do not
     * filter on this". A Criteria builder would be the textbook answer and
     * would be harder to read than the four clauses it replaces.
     *
     * <p><b>The search term is an empty string when unused, never null.</b>
     * PostgreSQL types every parameter, and a bare null inside {@code lower(?)}
     * has no type to infer, so it arrives as {@code bytea} and the statement
     * fails with "function lower(bytea) does not exist". H2 accepts it, which
     * is why the whole test suite passed while the deployed page returned 500.
     * An empty string is a string, so the driver types it as one and both
     * databases plan the same query.
     *
     * <p>The enum filters keep their nulls: each is compared with {@code =}
     * against a mapped column in the same clause, which is what gives Hibernate
     * the type to bind. Nothing wraps them in a function call.
     *
     * <p><b>The category filter is {@code member of}, not a join.</b> Joining
     * the categories table returns a supplier once per category it stocks,
     * which needed a {@code distinct} to hide -- and PostgreSQL rejects
     * {@code select distinct} whose {@code order by} names an expression that
     * is not in the select list, which the export-stance ordering below is.
     * H2 accepts both, so this was the second half of the same 500. An exists
     * subquery matches one supplier once, so neither is needed.
     *
     * <p>Ordered so the entries a buyer can act on come first: confirmed
     * exporters, then those who might, then the ones nobody has asked, then
     * EU-only. Within a group, by name -- alphabetical is the only order a
     * reader can predict, and the page already says how fresh each entry is.
     */
    @Query("""
            select s from Supplier s
            where (:category is null or :category member of s.categories)
              and (:trade is null or s.trade = :trade)
              and (:stance is null or s.exportStance = :stance)
              and (:term = ''
                   or lower(s.name) like lower(concat('%', :term, '%'))
                   or lower(s.city) like lower(concat('%', :term, '%'))
                   or lower(coalesce(s.region, '')) like lower(concat('%', :term, '%')))
            order by
              case s.exportStance
                when com.shoppingapp.shoppingwebapp.model.ExportStance.YES then 0
                when com.shoppingapp.shoppingwebapp.model.ExportStance.ON_REQUEST then 1
                when com.shoppingapp.shoppingwebapp.model.ExportStance.UNKNOWN then 2
                else 3
              end,
              s.name asc
            """)
    /** {@code term} must not be null; pass an empty string for "no text filter". */
    List<Supplier> search(@Param("category") Category category,
                          @Param("trade") SupplierTrade trade,
                          @Param("stance") ExportStance stance,
                          @Param("term") String term);

    long countByExportStance(ExportStance stance);
}
