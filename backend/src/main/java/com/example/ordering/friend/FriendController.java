package com.example.ordering.friend;

import com.example.ordering.common.ApiResponse;
import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.capability.CapabilityRegistry;
import com.example.ordering.user.UserAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;
    private final CapabilityRegistry capabilities;

    public FriendController(FriendService friendService, CapabilityRegistry capabilities) {
        this.friendService = friendService;
        this.capabilities = capabilities;
    }

    @GetMapping
    public ApiResponse<FriendCenterResponse> center(Authentication authentication) {
        return ApiResponse.ok(friendService.center(currentUser(authentication)));
    }

    @GetMapping("/search")
    public ApiResponse<List<FriendUserResponse>> search(
            Authentication authentication,
            @RequestParam(defaultValue = "") String query
    ) {
        return ApiResponse.ok(friendService.searchUsers(currentUser(authentication), query));
    }

    @PostMapping("/requests/{userId}")
    public ApiResponse<FriendRequestResponse> request(
            Authentication authentication,
            @PathVariable Long userId
    ) {
        return ApiResponse.ok("好友申请已发送", friendService.requestFriend(currentUser(authentication), userId));
    }

    @PostMapping("/requests/{friendshipId}/accept")
    public ApiResponse<FriendRequestResponse> accept(
            Authentication authentication,
            @PathVariable Long friendshipId
    ) {
        return ApiResponse.ok("已添加好友", friendService.accept(currentUser(authentication), friendshipId));
    }

    @PostMapping("/requests/{friendshipId}/reject")
    public ApiResponse<FriendRequestResponse> reject(
            Authentication authentication,
            @PathVariable Long friendshipId
    ) {
        return ApiResponse.ok("已拒绝好友申请", friendService.reject(currentUser(authentication), friendshipId));
    }

    @GetMapping("/{userId}/messages")
    public ApiResponse<List<FriendMessageResponse>> messages(
            Authentication authentication,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "80") int limit
    ) {
        if (limit < 1 || limit > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 200 之间");
        }
        return ApiResponse.ok(friendService.messages(currentUser(authentication), userId, limit));
    }

    @PostMapping("/{userId}/messages")
    public ApiResponse<FriendMessageResponse> sendMessage(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestBody FriendMessageRequest request
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("friendUserId", userId);
        input.put("content", request.content());
        if (request.replyToMessageId() != null) input.put("replyToMessageId", request.replyToMessageId());
        var result = capabilities.execute("friend.message", input, CapabilityExecutionContext.from(authentication));
        return ApiResponse.ok((FriendMessageResponse) result.get("message"));
    }

    @PostMapping(value = "/{userId}/messages/image", consumes = "multipart/form-data")
    public ApiResponse<FriendMessageResponse> sendImage(
            Authentication authentication,
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ApiResponse.ok("图片已发送", friendService.sendImage(currentUser(authentication), userId, file));
    }

    @PostMapping("/{userId}/messages/sticker/{stickerId}")
    public ApiResponse<FriendMessageResponse> sendSticker(
            Authentication authentication,
            @PathVariable Long userId,
            @PathVariable Long stickerId
    ) {
        return ApiResponse.ok(friendService.sendSticker(currentUser(authentication), userId, stickerId));
    }

    @GetMapping("/media/stickers")
    public ApiResponse<List<FriendStickerResponse>> stickers(Authentication authentication) {
        return ApiResponse.ok(friendService.stickers(currentUser(authentication)));
    }

    @PostMapping(value = "/media/stickers", consumes = "multipart/form-data")
    public ApiResponse<FriendStickerResponse> addSticker(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ApiResponse.ok("自定义表情已添加", friendService.addSticker(currentUser(authentication), file));
    }

    @GetMapping("/media/messages/{messageId}/content")
    public ResponseEntity<byte[]> messageMedia(
            Authentication authentication,
            @PathVariable Long messageId
    ) throws IOException {
        return mediaResponse(friendService.messageMedia(currentUser(authentication), messageId));
    }

    @GetMapping("/media/stickers/{stickerId}/content")
    public ResponseEntity<byte[]> stickerMedia(
            Authentication authentication,
            @PathVariable Long stickerId
    ) throws IOException {
        return mediaResponse(friendService.stickerMedia(currentUser(authentication), stickerId));
    }

    @PostMapping("/{userId}/read")
    public ApiResponse<Void> markRead(
            Authentication authentication,
            @PathVariable Long userId
    ) {
        friendService.markRead(currentUser(authentication), userId);
        return ApiResponse.ok("已读", null);
    }

    @DeleteMapping("/{userId}/messages")
    public ApiResponse<Void> clearMessages(
            Authentication authentication,
            @PathVariable Long userId
    ) {
        friendService.clearMessages(currentUser(authentication), userId);
        return ApiResponse.ok("聊天记录已删除", null);
    }

    private UserAccount currentUser(Authentication authentication) {
        return CapabilityExecutionContext.from(authentication).toUserAccount();
    }

    private ResponseEntity<byte[]> mediaResponse(FriendMediaContent media) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("Content-Type", media.contentType())
                .contentLength(media.bytes().length)
                .body(media.bytes());
    }
}
