// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

final class IdIndexEntriesExternalizer implements DataExternalizer<Collection<IdIndexEntry>> {
  private static final ThreadLocalCachedIntArray spareBufferLocal = new ThreadLocalCachedIntArray();

  @Override
  public void save(@NotNull DataOutput out, @NotNull Collection<IdIndexEntry> value) throws IOException {
    int size = value.size();
    int[] values = spareBufferLocal.getBuffer(size);
    int ptr = 0;
    for (IdIndexEntry ie : value) {
      values[ptr++] = ie.getWordHashCode();
    }
    save(out, values, size);
  }

  /** BEWARE: idHashes is _modified_ (sorted) during the method call */
  private static void save(@NotNull DataOutput out,
                           int[] idHashes,
                           int size) throws IOException {
    Arrays.sort(idHashes, 0, size);
    DataInputOutputUtil.writeDiffCompressed(out, idHashes, size);
  }

  @Override
  public Collection<IdIndexEntry> read(@NotNull DataInput in) throws IOException {
    //decode diff-compressed array (see DataInputOutputUtil.writeDiffCompressed() for a format):
    int length = DataInputOutputUtil.readINT(in);
    //TODO RC: create implementation of List<IdIndexEntry> that stores int[] inside
    ArrayList<IdIndexEntry> entries = new ArrayList<>(length);
    int prev = 0;
    while (length-- > 0) {
      int l = (int)(DataInputOutputUtil.readLONG(in) + prev);
      entries.add(new IdIndexEntry(l));
      prev = l;
    }
    return entries;
  }
}
