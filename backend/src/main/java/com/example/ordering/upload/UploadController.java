package com.example.ordering.upload;

import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.capability.CapabilityRegistry;
import com.example.ordering.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final CapabilityRegistry capabilities;

    public UploadController(CapabilityRegistry capabilities) {
        this.capabilities = capabilities;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponse> uploadImage(Authentication authentication,
                                                  @RequestParam("file") MultipartFile file) {
        var result = capabilities.execute("image.upload", Map.of("file", file),
                CapabilityExecutionContext.from(authentication));
        return ApiResponse.ok("图片上传成功", (UploadResponse) result.get("upload"));
    }
}
