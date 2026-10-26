package com.revenda.common.crypto;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class InMemoryDataKeys implements DataKeys {

    private final SecureRandom random = new SecureRandom();
    private final Map<DataKeyPurpose, List<DataKey>> keys = new EnumMap<>(DataKeyPurpose.class);

    InMemoryDataKeys() {
        for (DataKeyPurpose purpose : DataKeyPurpose.values()) {
            rotate(purpose);
        }
    }

    void rotate(DataKeyPurpose purpose) {
        byte[] material = new byte[32];
        random.nextBytes(material);
        List<DataKey> versions = keys.computeIfAbsent(purpose, key -> new ArrayList<>());
        versions.addFirst(new DataKey(versions.size() + 1, material));
    }

    void forgetAllBut(DataKeyPurpose purpose, int version) {
        keys.get(purpose).removeIf(key -> key.version() != version);
    }

    @Override
    public DataKey active(DataKeyPurpose purpose) {
        return keys.get(purpose).getFirst();
    }

    @Override
    public DataKey ofVersion(DataKeyPurpose purpose, int version) {
        return keys.get(purpose).stream().filter(key -> key.version() == version).findFirst()
                .orElseThrow(() -> new IllegalStateException("Sem a versão " + version));
    }

    @Override
    public List<DataKey> usable(DataKeyPurpose purpose) {
        return List.copyOf(keys.get(purpose));
    }
}
