package com.kateringapp.backend.services;

import com.kateringapp.backend.configurations.SpringContextRetriever;
import com.kateringapp.backend.dtos.OrderStatisticsDTO;
import com.kateringapp.backend.dtos.criteria.OrderStatisticCriteria;
import com.kateringapp.backend.entities.QMeal;
import com.kateringapp.backend.entities.order.OrderStatus;
import com.kateringapp.backend.entities.order.QOrder;
import com.kateringapp.backend.services.interfaces.IStatistics;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.querydsl.core.types.dsl.Expressions.dateTemplate;

@Service
@RequiredArgsConstructor
public class StatisticsService implements IStatistics {

    @PersistenceContext
    private final EntityManager entityManager;
    private final SpringContextRetriever springContextRetriever;

    @Override
    public List<OrderStatisticsDTO> getOrderStatistics(OrderStatisticCriteria criteria) {
        QOrder qOrder = QOrder.order;
        QMeal qMeal = QMeal.meal;
        UUID cateringFirmId = springContextRetriever.getCurrentUserIdFromJwt();

        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(qMeal.cateringFirmData.cateringFirmId.eq(cateringFirmId))
                .and(qOrder.orderStatus.eq(OrderStatus.COMPLETED));

        if(criteria.getStatisticsPeriod() == null) {
            if (criteria.getStartDate() != null) {
                conditions.and(qOrder.completedAt.goe(criteria.getStartDate()));
            }
            if (criteria.getEndDate() != null) {
                conditions.and(qOrder.completedAt.loe(criteria.getEndDate()));
            }
        }
        else{
            switch(criteria.getStatisticsPeriod()){
                case WEEK -> conditions.and(qOrder.completedAt.goe(Timestamp.from(Instant.from(LocalDate.now().minusWeeks(1)))));
                case MONTH -> conditions.and(qOrder.completedAt.goe(Timestamp.from(Instant.from(LocalDate.now().minusMonths(1)))));
                case YEAR -> conditions.and(qOrder.completedAt.goe(Timestamp.from(Instant.from(LocalDate.now().minusYears(1)))));
            }
        }

        JPAQuery<OrderStatisticsDTO> query = new JPAQuery<>(entityManager)
                .select(
                        Projections.constructor(
                                OrderStatisticsDTO.class,
                                dateTemplate(Date.class, "CAST({0} AS DATE)", qOrder.completedAt).as("date"),
                                qMeal.price.sum().as("sale")
                        )
                )
                .from(qOrder)
                .join(qOrder.meals, qMeal)
                .where(conditions)
                .groupBy(dateTemplate(String.class, "CAST({0} AS DATE)", qOrder.completedAt))
                .orderBy(dateTemplate(String.class, "CAST({0} AS DATE)", qOrder.completedAt).asc());

        return query.fetch();
    }

}
