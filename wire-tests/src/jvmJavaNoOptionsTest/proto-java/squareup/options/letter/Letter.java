// Code generated by Wire protocol buffer compiler, do not edit.
// Source file: letter.proto
package squareup.options.letter;

import com.squareup.wire.FieldEncoding;
import com.squareup.wire.Message;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.ProtoReader;
import com.squareup.wire.ProtoWriter;
import com.squareup.wire.WireField;
import com.squareup.wire.internal.Internal;
import java.io.IOException;
import java.lang.Boolean;
import java.lang.Deprecated;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.util.List;
import okio.ByteString;

public final class Letter extends Message<Letter, Letter.Builder> {
  public static final ProtoAdapter<Letter> ADAPTER = new ProtoAdapter_Letter();

  private static final long serialVersionUID = 0L;

  public static final String DEFAULT_TITLE = "";

  public static final Style DEFAULT_STYLE = Style.SHORT;

  public static final Boolean DEFAULT_ABOUT_LOVE = true;

  @WireField(
      tag = 1,
      adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  public final String title;

  @WireField(
      tag = 2,
      adapter = "squareup.options.letter.Style#ADAPTER"
  )
  public final Style style;

  @WireField(
      tag = 3,
      adapter = "com.squareup.wire.ProtoAdapter#BOOL"
  )
  public final Boolean about_love;

  @WireField(
      tag = 4,
      adapter = "com.squareup.wire.ProtoAdapter#INT32",
      label = WireField.Label.PACKED
  )
  @Deprecated
  public final List<Integer> path;

  public Letter(String title, Style style, Boolean about_love, List<Integer> path) {
    this(title, style, about_love, path, ByteString.EMPTY);
  }

  public Letter(String title, Style style, Boolean about_love, List<Integer> path,
      ByteString unknownFields) {
    super(ADAPTER, unknownFields);
    this.title = title;
    this.style = style;
    this.about_love = about_love;
    this.path = Internal.immutableCopyOf("path", path);
  }

  @Override
  public Builder newBuilder() {
    Builder builder = new Builder();
    builder.title = title;
    builder.style = style;
    builder.about_love = about_love;
    builder.path = Internal.copyOf(path);
    builder.addUnknownFields(unknownFields());
    return builder;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof Letter)) return false;
    Letter o = (Letter) other;
    return unknownFields().equals(o.unknownFields())
        && Internal.equals(title, o.title)
        && Internal.equals(style, o.style)
        && Internal.equals(about_love, o.about_love)
        && path.equals(o.path);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode;
    if (result == 0) {
      result = unknownFields().hashCode();
      result = result * 37 + (title != null ? title.hashCode() : 0);
      result = result * 37 + (style != null ? style.hashCode() : 0);
      result = result * 37 + (about_love != null ? about_love.hashCode() : 0);
      result = result * 37 + path.hashCode();
      super.hashCode = result;
    }
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (title != null) builder.append(", title=").append(title);
    if (style != null) builder.append(", style=").append(style);
    if (about_love != null) builder.append(", about_love=").append(about_love);
    if (!path.isEmpty()) builder.append(", path=").append(path);
    return builder.replace(0, 2, "Letter{").append('}').toString();
  }

  public static final class Builder extends Message.Builder<Letter, Builder> {
    public String title;

    public Style style;

    public Boolean about_love;

    public List<Integer> path;

    public Builder() {
      path = Internal.newMutableList();
    }

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder style(Style style) {
      this.style = style;
      return this;
    }

    public Builder about_love(Boolean about_love) {
      this.about_love = about_love;
      return this;
    }

    @Deprecated
    public Builder path(List<Integer> path) {
      Internal.checkElementsNotNull(path);
      this.path = path;
      return this;
    }

    @Override
    public Letter build() {
      return new Letter(title, style, about_love, path, super.buildUnknownFields());
    }
  }

  private static final class ProtoAdapter_Letter extends ProtoAdapter<Letter> {
    public ProtoAdapter_Letter() {
      super(FieldEncoding.LENGTH_DELIMITED, Letter.class);
    }

    @Override
    public int encodedSize(Letter value) {
      return ProtoAdapter.STRING.encodedSizeWithTag(1, value.title)
          + Style.ADAPTER.encodedSizeWithTag(2, value.style)
          + ProtoAdapter.BOOL.encodedSizeWithTag(3, value.about_love)
          + ProtoAdapter.INT32.asPacked().encodedSizeWithTag(4, value.path)
          + value.unknownFields().size();
    }

    @Override
    public void encode(ProtoWriter writer, Letter value) throws IOException {
      ProtoAdapter.STRING.encodeWithTag(writer, 1, value.title);
      Style.ADAPTER.encodeWithTag(writer, 2, value.style);
      ProtoAdapter.BOOL.encodeWithTag(writer, 3, value.about_love);
      ProtoAdapter.INT32.asPacked().encodeWithTag(writer, 4, value.path);
      writer.writeBytes(value.unknownFields());
    }

    @Override
    public Letter decode(ProtoReader reader) throws IOException {
      Builder builder = new Builder();
      long token = reader.beginMessage();
      for (int tag; (tag = reader.nextTag()) != -1;) {
        switch (tag) {
          case 1: builder.title(ProtoAdapter.STRING.decode(reader)); break;
          case 2: {
            try {
              builder.style(Style.ADAPTER.decode(reader));
            } catch (ProtoAdapter.EnumConstantNotFoundException e) {
              builder.addUnknownField(tag, FieldEncoding.VARINT, (long) e.value);
            }
            break;
          }
          case 3: builder.about_love(ProtoAdapter.BOOL.decode(reader)); break;
          case 4: builder.path.add(ProtoAdapter.INT32.decode(reader)); break;
          default: {
            reader.readUnknownField(tag);
          }
        }
      }
      builder.addUnknownFields(reader.endMessageAndGetUnknownFields(token));
      return builder.build();
    }

    @Override
    public Letter redact(Letter value) {
      Builder builder = value.newBuilder();
      builder.clearUnknownFields();
      return builder.build();
    }
  }
}