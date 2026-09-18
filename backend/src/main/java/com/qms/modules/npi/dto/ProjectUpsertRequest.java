package com.qms.modules.npi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 新品项目立项/编辑（仅立项草稿阶段允许改头信息）。 */
@Data
public class ProjectUpsertRequest {

    private Long id;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200)
    private String projectName;

    @NotNull(message = "拟引入品类不能为空")
    private Long categoryId;

    @Size(max = 100)
    private String brand;

    @Size(max = 1000)
    private String background;

    private LocalDateTime meetingAt;

    @Size(max = 500)
    private String attendees;

    private LocalDate targetListingDate;
}
