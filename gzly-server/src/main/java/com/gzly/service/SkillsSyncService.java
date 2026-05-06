package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.compliance.ComplianceTextGuard;
import com.gzly.entity.SkillsChunk;
import com.gzly.entity.SkillsDocument;
import com.gzly.entity.SkillsSource;
import com.gzly.mapper.SkillsChunkMapper;
import com.gzly.mapper.SkillsDocumentMapper;
import com.gzly.mapper.SkillsSourceMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SkillsSyncService {

    private final SkillsSourceMapper sourceMapper;
    private final SkillsDocumentMapper documentMapper;
    private final SkillsChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;
    private final ComplianceTextGuard complianceTextGuard;

    @Value("${gzly.skills.local-path:/root/zhangxuefeng-skills}")
    private String defaultLocalPath;

    public SyncResult sync(String localPath) {
        Path root = Path.of(localPath == null || localPath.isBlank() ? defaultLocalPath : localPath).toAbsolutePath();
        if (!Files.isDirectory(root)) {
            SyncResult result = new SyncResult();
            result.setSourcePath(root.toString());
            result.setMessage("skills 本地目录不存在，未执行同步");
            return result;
        }

        SkillsSource source = ensureLocalSource(root);
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .limit(2_000)
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("读取 skills 目录失败: " + e.getMessage(), e);
        }

        int docs = 0;
        int chunks = 0;
        for (Path file : files) {
            try {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                if (raw.isBlank()) {
                    continue;
                }
                String hash = sha256(raw);
                String docKey = root.relativize(file).toString();
                String sanitized = complianceTextGuard.sanitizeText("skills_document", docKey, raw);
                SkillsDocument doc = upsertDocument(source.getId(), docKey, file, hash, raw, sanitized);
                chunkMapper.delete(new LambdaQueryWrapper<SkillsChunk>().eq(SkillsChunk::getDocumentId, doc.getId()));
                List<String> parts = splitChunks(sanitized);
                for (int i = 0; i < parts.size(); i++) {
                    SkillsChunk chunk = new SkillsChunk();
                    chunk.setDocumentId(doc.getId());
                    chunk.setChunkIndex(i);
                    chunk.setChunkText(parts.get(i));
                    chunk.setTagsJson(objectMapper.writeValueAsString(tagsFor(parts.get(i))));
                    chunk.setEmbeddingVector(objectMapper.writeValueAsString(lightVector(parts.get(i))));
                    chunk.setTokenCount(Math.max(1, parts.get(i).length() / 2));
                    chunk.setCreatedAt(LocalDateTime.now());
                    chunkMapper.insert(chunk);
                    chunks++;
                }
                docs++;
            } catch (Exception ignored) {
                // 单个文档失败不阻断本轮同步，reports 可由后续管理页补充。
            }
        }
        source.setLastSyncTime(LocalDateTime.now());
        sourceMapper.updateById(source);

        SyncResult result = new SyncResult();
        result.setSourceId(source.getId());
        result.setSourcePath(root.toString());
        result.setDocumentCount(docs);
        result.setChunkCount(chunks);
        result.setMessage("skills 同步完成");
        return result;
    }

    private SkillsSource ensureLocalSource(Path root) {
        SkillsSource source = sourceMapper.selectOne(new LambdaQueryWrapper<SkillsSource>()
                .eq(SkillsSource::getSourceType, "local")
                .eq(SkillsSource::getLocalPath, root.toString())
                .last("LIMIT 1"));
        if (source != null) {
            return source;
        }
        source = new SkillsSource();
        source.setSourceName("张雪峰.skills 本地策略库");
        source.setSourceType("local");
        source.setSourceUrl("");
        source.setLocalPath(root.toString());
        source.setBranch("");
        source.setEnabled(1);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        sourceMapper.insert(source);
        return source;
    }

    private SkillsDocument upsertDocument(Long sourceId, String docKey, Path file, String hash, String raw, String sanitized) throws Exception {
        SkillsDocument existing = documentMapper.selectOne(new LambdaQueryWrapper<SkillsDocument>()
                .eq(SkillsDocument::getSourceId, sourceId)
                .eq(SkillsDocument::getDocKey, docKey)
                .last("LIMIT 1"));
        String title = titleFrom(file, raw);
        String tagsJson = objectMapper.writeValueAsString(tagsFor(raw));
        if (existing == null) {
            SkillsDocument doc = new SkillsDocument();
            doc.setSourceId(sourceId);
            doc.setDocKey(docKey);
            doc.setTitle(title);
            doc.setFilePath(file.toString());
            doc.setContentHash(hash);
            doc.setRawContent(raw);
            doc.setSanitizedContent(sanitized);
            doc.setTagsJson(tagsJson);
            doc.setEnabled(1);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.insert(doc);
            return doc;
        }
        existing.setTitle(title);
        existing.setFilePath(file.toString());
        existing.setContentHash(hash);
        existing.setRawContent(raw);
        existing.setSanitizedContent(sanitized);
        existing.setTagsJson(tagsJson);
        existing.setEnabled(1);
        existing.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(existing);
        return existing;
    }

    private boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt")
                || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json");
    }

    private List<String> splitChunks(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) return List.of();
        List<String> chunks = new ArrayList<>();
        int max = 1_200;
        for (int start = 0; start < value.length(); start += max) {
            int end = Math.min(value.length(), start + max);
            chunks.add(value.substring(start, end));
        }
        return chunks;
    }

    private String titleFrom(Path file, String raw) {
        String first = raw.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(file.getFileName().toString());
        return first.replaceFirst("^#+\\s*", "");
    }

    private List<String> tagsFor(String text) {
        String value = text == null ? "" : text;
        List<String> tags = new ArrayList<>();
        addIf(value, tags, "专业", "专业选择");
        addIf(value, tags, "就业", "就业优先");
        addIf(value, tags, "考研", "考研优先");
        addIf(value, tags, "考公", "考公优先");
        addIf(value, tags, "城市", "城市选择");
        addIf(value, tags, "医学", "医学类");
        addIf(value, tags, "计算机", "计算机类");
        addIf(value, tags, "师范", "师范类");
        addIf(value, tags, "法学", "法学类");
        addIf(value, tags, "财经", "财经类");
        addIf(value, tags, "土木", "避坑");
        addIf(value, tags, "机械", "避坑");
        addIf(value, tags, "材料", "避坑");
        addIf(value, tags, "化工", "避坑");
        addIf(value, tags, "民办", "民办和中外合作");
        addIf(value, tags, "中外合作", "民办和中外合作");
        addIf(value, tags, "冲", "冲稳保垫策略");
        return tags.isEmpty() ? List.of("公开策略") : tags;
    }

    private List<Double> lightVector(String text) {
        String value = text == null ? "" : text;
        List<String> anchors = List.of("就业", "考研", "考公", "城市", "学校", "专业", "医学", "计算机",
                "师范", "法学", "财经", "土木", "机械", "材料", "化工", "民办", "中外合作", "冲", "稳", "保");
        List<Double> vector = new ArrayList<>();
        int length = Math.max(1, value.length());
        for (String anchor : anchors) {
            vector.add(count(value, anchor) * 1.0D / length);
        }
        return vector;
    }

    private int count(String text, String keyword) {
        int count = 0;
        int index = 0;
        while (text != null && keyword != null && !keyword.isBlank()
                && (index = text.indexOf(keyword, index)) >= 0) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    private void addIf(String text, List<String> tags, String keyword, String tag) {
        if (text.contains(keyword) && !tags.contains(tag)) {
            tags.add(tag);
        }
    }

    private String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Data
    public static class SyncResult {
        private Long sourceId;
        private String sourcePath;
        private int documentCount;
        private int chunkCount;
        private String message;
    }
}
