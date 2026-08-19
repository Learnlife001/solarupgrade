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
     * <p>Ordered so the entries a buyer can act on come first: confirmed
     * exporters, then those who might, then the ones nobody has asked, then
     * EU-only. Within a group, by name -- alphabetical is the only order a
     * reader can predict, and the page already says how fresh each entry is.
     */
    @Query("""
            select distinct s from Supplier s
            left join s.categories c
            where (:category is null or c = :category)
              and (:trade is null or s.trade = :trade)
              and (:stance is null or s.exportStance = :stance)
              and (:term is null
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
    List<Supplier> search(@Param("category") Category category,
                          @Param("trade") SupplierTrade trade,
                          @Param("stance") ExportStance stance,
                          @Param("term") String term);

    long countByExportStance(ExportStance stance);
}
