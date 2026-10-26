package com.revenda.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class BlindIndexerTests {

    private static final String CPF = "52998224725";

    private final InMemoryDataKeys keys = new InMemoryDataKeys();
    private final BlindIndexer indexer = new BlindIndexer(keys);

    @Test
    void sameValueGivesTheSameIndexAndAnotherValueAnother() {
        byte[] index = indexer.index("cpf", CPF);

        assertThat(index).hasSize(32);
        assertThat(indexer.index("cpf", CPF)).isEqualTo(index);
        assertThat(indexer.index("cpf", "11144477735")).isNotEqualTo(index);
    }

    @Test
    void domainsAndKeysAreSeparated() throws NoSuchAlgorithmException {
        byte[] index = indexer.index("cpf", CPF);
        byte[] plainHash = MessageDigest.getInstance("SHA-256").digest(CPF.getBytes(StandardCharsets.UTF_8));

        assertThat(indexer.index("phone", CPF)).isNotEqualTo(index);
        assertThat(new BlindIndexer(new InMemoryDataKeys()).index("cpf", CPF)).isNotEqualTo(index);
        assertThat(index).isNotEqualTo(plainHash);
    }

    @Test
    void indexDoesNotUseTheFieldKey() {
        byte[] index = indexer.index("cpf", CPF);

        keys.rotate(DataKeyPurpose.FIELD);

        assertThat(indexer.index("cpf", CPF)).isEqualTo(index);
    }

    @Test
    void duringRotationTheSearchCoversTheOldKeys() {
        byte[] before = indexer.index("cpf", CPF);

        keys.rotate(DataKeyPurpose.BLIND_INDEX);

        byte[] after = indexer.index("cpf", CPF);
        assertThat(after).isNotEqualTo(before);
        assertThat(indexer.candidates("cpf", CPF)).containsExactly(after, before);
    }

    @Test
    void nothingToIndexStaysNull() {
        assertThat(indexer.index("cpf", null)).isNull();
    }
}
