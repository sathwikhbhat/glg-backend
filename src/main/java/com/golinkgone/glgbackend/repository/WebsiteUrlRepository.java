package com.golinkgone.glgbackend.repository;

import com.golinkgone.glgbackend.entity.LinkItemResponse;
import com.golinkgone.glgbackend.entity.LinkRef;
import com.golinkgone.glgbackend.entity.ResolvedLinkProjection;
import com.golinkgone.glgbackend.entity.WebsiteUrl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WebsiteUrlRepository extends JpaRepository<WebsiteUrl, UUID> {

    @Query("""
                SELECT w.linkId AS linkId, w.originalUrl AS originalUrl, w.userId AS userId
                FROM WebsiteUrl w
                WHERE w.shortKey = :shortKey
            """)
    ResolvedLinkProjection findResolvedByShortKey(@Param("shortKey") String shortKey);

    @Query("SELECT w.linkId FROM WebsiteUrl w WHERE w.shortKey = :shortKey AND w.userId = :userId")
    Optional<UUID> findLinkIdByShortKeyAndUserId(@Param("shortKey") String shortKey, @Param("userId") UUID userId);

    @Query("""
                SELECT new com.golinkgone.glgbackend.entity.LinkRef(w.shortKey, w.linkId)
                FROM WebsiteUrl w
                WHERE w.userId = :userId
            """)
    List<LinkRef> findAllLinkRefsByUserId(@Param("userId") UUID userId);

    @Query("SELECT u.shortKey FROM WebsiteUrl u")
    List<String> findAllShortKeys();

    @Modifying
    @Transactional
    @Query("DELETE FROM WebsiteUrl w WHERE w.shortKey = :shortKey AND w.userId = :userId")
    void deleteByShortKeyAndUserId(@Param("shortKey") String shortKey, @Param("userId") UUID userId);

    @Query("""
                SELECT new com.golinkgone.glgbackend.entity.LinkItemResponse(
                    w.shortKey, w.shortKey, w.originalUrl, w.createdAt
                )
                FROM WebsiteUrl w
                WHERE w.userId = :userId
            """)
    Page<LinkItemResponse> findUserLinks(@Param("userId") UUID userId, Pageable pageable);
}
