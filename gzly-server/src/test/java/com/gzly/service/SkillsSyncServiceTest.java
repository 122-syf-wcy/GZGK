package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.compliance.ComplianceTextGuard;
import com.gzly.entity.SkillsChunk;
import com.gzly.entity.SkillsDocument;
import com.gzly.entity.SkillsSource;
import com.gzly.mapper.SkillsChunkMapper;
import com.gzly.mapper.SkillsDocumentMapper;
import com.gzly.mapper.SkillsSourceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SkillsSyncService 行为测试。
 *
 * <ul>
 *   <li>路径不存在时返回 message，不写库。</li>
 *   <li>真实 tempDir 内 .md/.txt 文件 → 写入 source/document/chunk，并对每个文档调用 sanitize。</li>
 *   <li>chunk 含 tagsJson + embeddingVector + tokenCount。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillsSyncServiceTest {

    @Mock SkillsSourceMapper sourceMapper;
    @Mock SkillsDocumentMapper documentMapper;
    @Mock SkillsChunkMapper chunkMapper;
    @Mock ComplianceTextGuard complianceTextGuard;

    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks SkillsSyncService service;

    @Test
    void sync_returnsMessage_whenLocalPathMissing() {
        SkillsSyncService.SyncResult result = service.sync("/tmp/nonexistent-skills-" + System.nanoTime());

        assertThat(result.getMessage()).contains("不存在");
        assertThat(result.getDocumentCount()).isZero();
        assertThat(result.getChunkCount()).isZero();
        verify(sourceMapper, atLeast(0)).insert(any());
    }

    @Test
    void sync_processesMarkdownFilesAndPersistsChunks(@TempDir Path tempDir) throws Exception {
        // 准备两个 markdown 文件
        Files.writeString(tempDir.resolve("major.md"),
                "# 专业选择策略\n\n本文档说明就业优先 vs 考研优先 vs 考公优先 的取舍要点。计算机、医学、师范、法学是热门专业。");
        Files.writeString(tempDir.resolve("city.txt"),
                "城市优先：一线城市的就业机会密度更高。建议优先考虑北京、上海、广州、深圳。");

        // sourceMapper.selectOne 第一次返回 null（新增），insert 后赋 id=1
        AtomicLong sourceIdSequence = new AtomicLong(1L);
        when(sourceMapper.selectOne(any())).thenReturn(null);
        when(sourceMapper.insert(any(SkillsSource.class))).thenAnswer(inv -> {
            SkillsSource s = inv.getArgument(0);
            s.setId(sourceIdSequence.getAndIncrement());
            return 1;
        });
        // documentMapper.selectOne 返回 null，insert 后赋递增 id
        AtomicLong docIdSeq = new AtomicLong(1L);
        when(documentMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.insert(any(SkillsDocument.class))).thenAnswer(inv -> {
            SkillsDocument d = inv.getArgument(0);
            d.setId(docIdSeq.getAndIncrement());
            return 1;
        });
        when(complianceTextGuard.sanitizeText(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2, String.class));

        SkillsSyncService.SyncResult result = service.sync(tempDir.toString());

        assertThat(result.getMessage()).contains("完成");
        assertThat(result.getDocumentCount()).isEqualTo(2);
        assertThat(result.getChunkCount()).isGreaterThanOrEqualTo(2);

        // 必定调用 sanitize：每个 doc 1 次（skills_document）
        verify(complianceTextGuard, atLeastOnce()).sanitizeText(eq("skills_document"), anyString(), anyString());
        verify(documentMapper, atLeast(2)).insert(any(SkillsDocument.class));
        verify(chunkMapper, atLeastOnce()).insert(any(SkillsChunk.class));

        // 检查 chunk 的 embeddingVector / tagsJson / tokenCount 字段已填
        ArgumentCaptor<SkillsChunk> captor = ArgumentCaptor.forClass(SkillsChunk.class);
        verify(chunkMapper, atLeastOnce()).insert(captor.capture());
        for (SkillsChunk chunk : captor.getAllValues()) {
            assertThat(chunk.getEmbeddingVector()).startsWith("[").endsWith("]");
            assertThat(chunk.getTagsJson()).startsWith("[").endsWith("]");
            assertThat(chunk.getTokenCount()).isGreaterThan(0);
            assertThat(chunk.getChunkText()).isNotBlank();
            assertThat(chunk.getCreatedAt()).isNotNull();
        }
    }

    @Test
    void sync_supportedExtensionsCoverMarkdownTxtYamlJson(@TempDir Path tempDir) throws Exception {
        // 一个不支持的 .pdf 文件 + 一个支持的 .yaml
        Files.writeString(tempDir.resolve("ignored.pdf"), "binary-ish content");
        Files.writeString(tempDir.resolve("rules.yaml"), "tag: 专业选择\nadvice: 看就业");
        when(sourceMapper.selectOne(any())).thenReturn(null);
        when(sourceMapper.insert(any(SkillsSource.class))).thenAnswer(inv -> {
            SkillsSource s = inv.getArgument(0);
            s.setId(1L);
            return 1;
        });
        when(documentMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.insert(any(SkillsDocument.class))).thenAnswer(inv -> {
            SkillsDocument d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        when(complianceTextGuard.sanitizeText(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2, String.class));

        SkillsSyncService.SyncResult result = service.sync(tempDir.toString());

        // 只 yaml 被处理，pdf 跳过
        assertThat(result.getDocumentCount()).isEqualTo(1);
        assertThat(result.getChunkCount()).isGreaterThanOrEqualTo(1);
    }
}
