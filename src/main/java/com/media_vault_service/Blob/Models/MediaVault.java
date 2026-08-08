package com.media_vault_service.Blob.Models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    // @Lob tells JPA to store this as a large binary object (bytea in Postgres)
    @Lob
    private byte[] encryptedData;
}