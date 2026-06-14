package com.teamup.teamup.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for GET /api/groups/{groupId}/files — the "Group Drive".
 * Returns all submitted files across all tasks within a group.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupFileListDto {

    private Long groupId;
    private String groupName;
    private Integer totalFiles;
    private Long totalSizeBytes;
    private List<GroupFileDto> files;

    /** Summary metadata for the file table header. */
    private Summary summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private Integer uniqueUploaders;
        private Integer uniqueTasks;
        private String lastUploadedAt;
        private String oldestFile;
        private String newestFile;
    }
}
