package com.media_vault_service.Blob.Repositories;

import com.media_vault_service.Blob.Models.MediaVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaVaultRepository extends JpaRepository<MediaVault, String> {

}