package com.media_vault_service.Blob.Models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;

@Entity
@Table(name = "media_vault")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaVault {

    @Id
    private String id;

    private String uploaderId;
    private String fileName;
    private String fileType;

    // 🟢 CRITICAL FIX: Forces Postgres to use bytea instead of oid for binary files
    @Lob
    @JdbcTypeCode(Types.BINARY)
    private byte[] encryptedData;
}