package com.iflytek.skillhub.dto;

import java.util.List;

public record ReviewVersionSnapshotResponse(
        Long versionId,
        String version,
        String status,
        String parsedMetadataJson,
        List<SkillFileResponse> files,
        String documentationPath,
        String documentationContent
) {}
