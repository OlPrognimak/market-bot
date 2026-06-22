package com.prognimak.marketbot.system.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Stores selectable external API credentials for market-data, AI, and messaging providers.
 */
@Entity
@Table(name = "system_api_credential", uniqueConstraints = @UniqueConstraint(
        name = "uk_system_api_credential_name",
        columnNames = {"credential_type", "provider_name", "display_name"}
))
@Getter
@Setter
public class SystemApiCredentialEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 40)
    private SystemCredentialType credentialType;

    @Column(name = "provider_name", nullable = false, length = 60)
    private String providerName;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "secret_value", length = 2000)
    private String secretValue;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 255)
    private String description;
}
