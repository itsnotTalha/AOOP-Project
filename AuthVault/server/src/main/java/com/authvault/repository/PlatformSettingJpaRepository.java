package com.authvault.repository;

import com.authvault.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingJpaRepository extends JpaRepository<PlatformSetting, String> {}
