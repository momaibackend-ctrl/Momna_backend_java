package com.momna.modules.profile.infrastructure;

import com.momna.modules.profile.domain.AccountStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_profiles", schema = "momna")
public class UserProfileEntity {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "preferred_language")
    private String preferredLanguage;

    private String locale;

    @Column(name = "country_region")
    private String countryRegion;

    private String timezone;

    @Column(name = "measurement_system")
    private String measurementSystem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unit_preferences", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> unitPreferences = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_preferences", nullable = false, columnDefinition = "jsonb")
    private Map<String, Boolean> notificationPreferences = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfileEntity() {}

    public UserProfileEntity(String userId, Instant now) {
        this.userId = userId;
        this.updatedAt = now;
    }

    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public String getLocale() { return locale; }
    public String getCountryRegion() { return countryRegion; }
    public String getTimezone() { return timezone; }
    public String getMeasurementSystem() { return measurementSystem; }
    public Map<String, String> getUnitPreferences() { return Map.copyOf(unitPreferences); }
    public Map<String, Boolean> getNotificationPreferences() { return Map.copyOf(notificationPreferences); }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public long getVersion() { return version; }

    public void updatePreferences(
        String measurementSystem,
        Map<String, String> unitPreferences,
        Map<String, Boolean> notificationPreferences,
        Instant now
    ) {
        this.measurementSystem = measurementSystem;
        this.unitPreferences = new HashMap<>(unitPreferences);
        this.notificationPreferences = new HashMap<>(notificationPreferences);
        this.updatedAt = now;
    }

    public void updateLocalization(
        String preferredLanguage, String locale, String countryRegion, String timezone, Instant now
    ) {
        this.preferredLanguage = preferredLanguage;
        this.locale = locale;
        this.countryRegion = countryRegion;
        this.timezone = timezone;
        this.updatedAt = now;
    }
}
