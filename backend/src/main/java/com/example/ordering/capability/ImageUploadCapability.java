package com.example.ordering.capability;
import org.springframework.stereotype.Component;
import com.example.ordering.user.UserRole;
import java.util.Set;
import java.util.Map;
@Component public final class ImageUploadCapability implements Capability {
    public static final String ID = "image.upload";
    public String id() { return ID; }
    public String name() { return "上传图片"; }
    public String description() { return "上传受鉴权保护的图片资源"; }
    public String category() { return CapabilityCategory.CREATION.id(); }
    public String route() { return "/api/uploads"; }
    public Set<UserRole> visibleTo() { return Set.of(UserRole.ADMIN, UserRole.SUPER_ADMIN); }
    public Map<String, Object> inputSchema() {
        return Map.of("file", Map.of("type", "string", "format", "binary", "required", true));
    }
}
