package com.vaultchain.repository;

import com.vaultchain.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingJpaRepository extends JpaRepository<PlatformSetting, String> {}
