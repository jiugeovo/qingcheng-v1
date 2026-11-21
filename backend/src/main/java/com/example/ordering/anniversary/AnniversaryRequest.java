package com.example.ordering.anniversary;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Ownership comes from authentication. Omitted/null type preserves existing classification on update. */
@JsonDeserialize(using = AnniversaryRequestDeserializer.class)
public record AnniversaryRequest(
        @NotBlank(message = "标题不能为空") @Size(max = 100, message = "标题不能超过 100 字") String title,
        @NotNull(message = "日期不能为空") LocalDate date,
        @Size(max = 1000, message = "备注不能超过 1000 字") String note,
        Boolean repeatYearly,
        AnniversaryType type
) {
    public AnniversaryRequest {
        title = title == null ? null : title.strip();
        note = note == null ? "" : note.strip();
        repeatYearly = repeatYearly == null ? Boolean.TRUE : repeatYearly;
    }

    @AssertTrue(message = "日期年份必须在 1000 到 9999 之间")
    @JsonIgnore
    public boolean isDateInRange() {
        return date == null || (date.getYear() >= 1000 && date.getYear() <= 9999);
    }
}
