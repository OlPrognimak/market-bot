package com.prognimak.marketbot.entity;

import com.prognimak.marketbot.user.model.UserPropertyType;
import com.prognimak.marketbot.user.model.UserPropertyValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "app_user_property",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_app_user_property_user_type_name",
                        columnNames = {"user_id", "property_type", "property_name"}
                )
        }
)
@Getter
@Setter
public class AppUserPropertyEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 40)
    private UserPropertyType propertyType;

    @Column(name = "property_name", nullable = false, length = 160)
    private String propertyName;

    @Column(name = "property_value", length = 2000)
    private String propertyValue;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_value_type", nullable = false, length = 40)
    private UserPropertyValueType propertyValueType = UserPropertyValueType.TEXT;
}
