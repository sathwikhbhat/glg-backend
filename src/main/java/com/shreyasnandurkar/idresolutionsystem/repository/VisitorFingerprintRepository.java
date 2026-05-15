package com.shreyasnandurkar.idresolutionsystem.repository;

import com.shreyasnandurkar.idresolutionsystem.entity.VisitorFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitorFingerprintRepository extends JpaRepository<VisitorFingerprint, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO visitor_fingerprint (short_key, ip_address_hash)
            VALUES (:shortKey, :ipHash)
            ON CONFLICT ON CONSTRAINT uk_visitor_shortkey_iphash DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("shortKey") String shortKey, @Param("ipHash") String ipHash);
}
