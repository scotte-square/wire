// Code generated by Wire protocol buffer compiler, do not edit.
// Source: squareup.geology.java.Period in period_java.proto
package com.squareup.wire.proto2.geology.java;

import com.squareup.wire.EnumAdapter;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.Syntax;
import com.squareup.wire.WireEnum;
import java.lang.Override;

public enum Period implements WireEnum {
  /**
   * 145.5 million years ago — 66.0 million years ago.
   */
  CRETACEOUS(1),

  /**
   * 201.3 million years ago — 145.0 million years ago.
   */
  JURASSIC(2),

  /**
   * 252.17 million years ago — 201.3 million years ago.
   */
  TRIASSIC(3);

  public static final ProtoAdapter<Period> ADAPTER = new ProtoAdapter_Period();

  private final int value;

  Period(int value) {
    this.value = value;
  }

  /**
   * Return the constant for {@code value} or null.
   */
  public static Period fromValue(int value) {
    switch (value) {
      case 1: return CRETACEOUS;
      case 2: return JURASSIC;
      case 3: return TRIASSIC;
      default: return null;
    }
  }

  @Override
  public int getValue() {
    return value;
  }

  private static final class ProtoAdapter_Period extends EnumAdapter<Period> {
    ProtoAdapter_Period() {
      super(Period.class, Syntax.PROTO_2);
    }

    @Override
    protected Period fromValue(int value) {
      return Period.fromValue(value);
    }
  }
}
