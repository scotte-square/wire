/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("UsePropertyAccessSyntax")

package com.squareup.wire

import com.google.gson.GsonBuilder
import com.google.protobuf.Any
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Duration
import com.google.protobuf.FieldOptions
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Timestamp
import com.google.protobuf.Value
import com.google.protobuf.util.JsonFormat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.wire.json.assertJsonEquals
import okio.ByteString
import okio.buffer
import okio.source
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.fail
import org.junit.Test
import squareup.proto3.kotlin.alltypes.All64OuterClass
import squareup.proto3.kotlin.alltypes.AllTypesOuterClass
import squareup.proto3.kotlin.alltypes.CamelCaseOuterClass
import squareup.proto3.kotlin.pizza.PizzaOuterClass
import java.io.File
import com.squareup.wire.proto3.kotlin.requiredextension.RequiredExtension as RequiredExtensionK
import com.squareup.wire.proto3.kotlin.requiredextension.RequiredExtensionMessage as RequiredExtensionMessageK
import squareup.proto3.kotlin.alltypes.All64 as All64K
import squareup.proto3.kotlin.alltypes.AllTypes as AllTypesK
import squareup.proto3.java.alltypes.AllTypes as AllTypesJ
import squareup.proto3.kotlin.pizza.BuyOneGetOnePromotion as BuyOneGetOnePromotionK
import squareup.proto3.kotlin.pizza.FreeGarlicBreadPromotion as FreeGarlicBreadPromotionK
import squareup.proto3.kotlin.pizza.Pizza as PizzaK
import squareup.proto3.kotlin.pizza.PizzaDelivery as PizzaDeliveryK

class Proto3WireProtocCompatibilityTests {
  // Note: this test mostly make sure we compile required extension without failing.
  @Test fun protocAndRequiredExtensions() {
    val wireMessage = RequiredExtensionMessageK("Yo")

    val googleMessage = RequiredExtensionK.RequiredExtensionMessage.newBuilder()
        .setStringField("Yo")
        .build()

    assertThat(wireMessage.encode()).isEqualTo(googleMessage.toByteArray())

    // Although the custom options has no label, it shouldn't be "required" to instantiate
    // `FieldOptions`. We should not fail.
    assertThat(DescriptorProtos.FieldOptions.newBuilder().build()).isNotNull()
    assertThat(FieldOptions()).isNotNull()
  }

  @Test fun protocJson() {
    val pizzaDelivery = PizzaOuterClass.PizzaDelivery.newBuilder()
        .setAddress("507 Cross Street")
        .setDeliveredWithinOrFree(Duration.newBuilder()
            .setSeconds(1_799)
            .setNanos(500_000_000)
            .build())
        .addPizzas(PizzaOuterClass.Pizza.newBuilder()
            .addToppings("pineapple")
            .addToppings("onion")
            .build())
        .setPromotion(Any.pack(PizzaOuterClass.BuyOneGetOnePromotion.newBuilder()
            .setCoupon("MAUI")
            .build()))
        .setOrderedAt(Timestamp.newBuilder()
            .setSeconds(-631152000L) // 1950-01-01T00:00:00.250Z.
            .setNanos(250_000_000)
            .build())
        .build()

    val json = """
        |{
        |  "address": "507 Cross Street",
        |  "pizzas": [{
        |    "toppings": ["pineapple", "onion"]
        |  }],
        |  "promotion": {
        |    "@type": "type.googleapis.com/squareup.proto3.kotlin.pizza.BuyOneGetOnePromotion",
        |    "coupon": "MAUI"
        |  },
        |  "deliveredWithinOrFree": "1799.500s",
        |  "orderedAt": "1950-01-01T00:00:00.250Z"
        |}
        """.trimMargin()

    val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
        .add(PizzaOuterClass.BuyOneGetOnePromotion.getDescriptor())
        .add(PizzaOuterClass.FreeGarlicBreadPromotion.getDescriptor())
        .build()

    val jsonPrinter = JsonFormat.printer()
        .usingTypeRegistry(typeRegistry)
    assertThat(jsonPrinter.print(pizzaDelivery)).isEqualTo(json)

    val jsonParser = JsonFormat.parser().usingTypeRegistry(typeRegistry)
    val parsed = PizzaOuterClass.PizzaDelivery.newBuilder()
        .apply { jsonParser.merge(json, this) }
        .build()
    assertThat(parsed).isEqualTo(pizzaDelivery)
  }

  @Test fun wireJson() {
    val pizzaDelivery = PizzaDeliveryK(
        address = "507 Cross Street",
        pizzas = listOf(PizzaK(toppings = listOf("pineapple", "onion"))),
        promotion = AnyMessage.pack(BuyOneGetOnePromotionK(coupon = "MAUI")),
        delivered_within_or_free = durationOfSeconds(1_799L, 500_000_000L),
        loyalty = emptyMap<String, Any?>(),
        ordered_at = ofEpochSecond(-631152000L, 250_000_000L)
    )
    val json = """
        |{
        |  "address": "507 Cross Street",
        |  "deliveredWithinOrFree": "1799.500s",
        |  "pizzas": [
        |    {
        |      "toppings": [
        |        "pineapple",
        |        "onion"
        |      ]
        |    }
        |  ],
        |  "loyalty": {},
        |  "promotion": {
        |    "@type": "type.googleapis.com/squareup.proto3.kotlin.pizza.BuyOneGetOnePromotion",
        |    "coupon": "MAUI"
        |  },
        |  "orderedAt": "1950-01-01T00:00:00.250Z"
        |}
        """.trimMargin()

    val moshi = Moshi.Builder()
        .add(WireJsonAdapterFactory()
            .plus(listOf(BuyOneGetOnePromotionK.ADAPTER, FreeGarlicBreadPromotionK.ADAPTER)))
        .build()

    val jsonAdapter = moshi.adapter(PizzaDeliveryK::class.java).indent("  ")
    assertJsonEquals(jsonAdapter.toJson(pizzaDelivery), json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(pizzaDelivery)
  }

  @Test fun gsonJson() {
    val pizzaDelivery = PizzaDeliveryK(
        address = "507 Cross Street",
        pizzas = listOf(PizzaK(toppings = listOf("pineapple", "onion"))),
        delivered_within_or_free = durationOfSeconds(1_799L, 500_000_000L)
    )
    val json = """
        |{
        |  "address": "507 Cross Street",
        |  "deliveredWithinOrFree": "1799.500s",
        |  "phoneNumber": "",
        |  "pizzas": [
        |    {
        |      "toppings": [
        |        "pineapple",
        |        "onion"
        |      ]
        |    }
        |  ]
        |}
        """.trimMargin()

    val gson = GsonBuilder().registerTypeAdapterFactory(WireTypeAdapterFactory())
        .disableHtmlEscaping()
        .create()

    assertJsonEquals(gson.toJson(pizzaDelivery), json)
    assertThat(gson.fromJson(json, PizzaDeliveryK::class.java)).isEqualTo(pizzaDelivery)
  }

  @Test fun wireProtocJsonRoundTrip() {
    val protocMessage = PizzaOuterClass.PizzaDelivery.newBuilder()
        .setAddress("507 Cross Street")
        .addPizzas(PizzaOuterClass.Pizza.newBuilder()
            .addToppings("pineapple")
            .addToppings("onion")
            .build())
        .setPromotion(Any.pack(PizzaOuterClass.BuyOneGetOnePromotion.newBuilder()
            .setCoupon("MAUI")
            .build()))
        .setLoyalty(Struct.newBuilder().build())
        .build()

    val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
        .add(PizzaOuterClass.BuyOneGetOnePromotion.getDescriptor())
        .add(PizzaOuterClass.FreeGarlicBreadPromotion.getDescriptor())
        .build()

    val jsonPrinter = JsonFormat.printer()
        .usingTypeRegistry(typeRegistry)
    val protocMessageJson = jsonPrinter.print(protocMessage)

    val wireMessage = PizzaDeliveryK(
        address = "507 Cross Street",
        pizzas = listOf(PizzaK(toppings = listOf("pineapple", "onion"))),
        promotion = AnyMessage.pack(BuyOneGetOnePromotionK(coupon = "MAUI")),
        loyalty = emptyMap<String, Any?>()
    )

    val moshi = Moshi.Builder()
        .add(WireJsonAdapterFactory()
            .plus(listOf(BuyOneGetOnePromotionK.ADAPTER, FreeGarlicBreadPromotionK.ADAPTER)))
        .build()
    val jsonAdapter = moshi.adapter(PizzaDeliveryK::class.java)
    val moshiMessageJson = jsonAdapter.toJson(wireMessage)

    // Parsing the two json because this should be order insensitive.
    assertJsonEquals(moshiMessageJson, protocMessageJson)

    // Now each parses the other's json.
    val jsonParser = JsonFormat.parser().usingTypeRegistry(typeRegistry)
    val protocMessageDecodedFromWireJson = PizzaOuterClass.PizzaDelivery.newBuilder()
        .apply { jsonParser.merge(moshiMessageJson, this) }
        .build()

    val wireMessageDecodedFromProtocJson = jsonAdapter.fromJson(protocMessageJson)

    assertThat(protocMessageDecodedFromWireJson).isEqualTo(protocMessage)
    assertThat(wireMessageDecodedFromProtocJson).isEqualTo(wireMessage)
  }

  @Test fun unregisteredTypeOnReading() {
    val jsonAdapter = moshi.adapter(PizzaDeliveryK::class.java)

    val json = """
        |{
        |  "address": "507 Cross Street",
        |  "pizzas": [
        |    {
        |      "toppings": [
        |        "pineapple",
        |        "onion"
        |      ]
        |    }
        |  ],
        |  "promotion": {
        |    "@type": "type.googleapis.com/squareup.proto3.kotlin.pizza.BuyOneGetOnePromotion",
        |    "coupon": "MAUI"
        |  }
        |}
        """.trimMargin()

    try {
      jsonAdapter.fromJson(json)
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("Cannot resolve type: " +
          "type.googleapis.com/squareup.proto3.kotlin.pizza.BuyOneGetOnePromotion in \$.promotion")
    }
  }

  @Test fun unregisteredTypeOnWriting() {
    val pizzaDelivery = PizzaDeliveryK(
        address = "507 Cross Street",
        pizzas = listOf(PizzaK(toppings = listOf("pineapple", "onion"))),
        promotion = AnyMessage.pack(BuyOneGetOnePromotionK(coupon = "MAUI"))
    )

    val jsonAdapter = moshi.adapter(PizzaDeliveryK::class.java)
    try {
      jsonAdapter.toJson(pizzaDelivery)
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected)
          .hasMessage("Cannot find type for url: " +
              "type.googleapis.com/squareup.proto3.kotlin.pizza.BuyOneGetOnePromotion " +
              "in \$.promotion.@type")
    }
  }

  @Test fun anyJsonWithoutType() {
    val jsonAdapter = moshi.adapter(PizzaDeliveryK::class.java)

    val json = """
        |{
        |  "address": "507 Cross Street",
        |  "pizzas": [
        |    {
        |      "toppings": [
        |        "pineapple",
        |        "onion"
        |      ]
        |    }
        |  ],
        |  "promotion": {
        |    "coupon": "MAUI"
        |  }
        |}
        """.trimMargin()

    try {
      jsonAdapter.fromJson(json)
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("expected @type in \$.promotion")
    }
  }

  @Test fun serializeDefaultAllTypesProtoc() {
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(DEFAULT_ALL_TYPES_JSON, jsonPrinter.print(defaultAllTypesProtoc))
  }

  @Test fun defaultAllTypes() {
    val protocBytes = defaultAllTypesProtoc.toByteArray()
    assertThat(AllTypesJ.ADAPTER.encode(defaultAllTypesWireJava)).isEqualTo(protocBytes)
    assertThat(AllTypesJ.ADAPTER.decode(protocBytes)).isEqualTo(defaultAllTypesWireJava)
    assertThat(AllTypesK.ADAPTER.encode(defaultAllTypesWireKotlin)).isEqualTo(protocBytes)
    assertThat(AllTypesK.ADAPTER.decode(protocBytes)).isEqualTo(defaultAllTypesWireKotlin)
  }

  @Test fun explicitIdentityAllTypes() {
    val protocBytes = explicitIdentityAllTypesProtoc.toByteArray()
    assertThat(AllTypesJ.ADAPTER.encode(explicitIdentityAllTypesWireJava)).isEqualTo(protocBytes)
    // TODO(benoit)
    // assertThat(AllTypesJ.ADAPTER.decode(protocBytes)).isEqualTo(explicitIdentityAllTypesWireJava)
    assertThat(AllTypesK.ADAPTER.encode(explicitIdentityAllTypesWireKotlin)).isEqualTo(protocBytes)
    assertThat(AllTypesK.ADAPTER.decode(protocBytes)).isEqualTo(explicitIdentityAllTypesWireKotlin)
  }

  @Test fun implicitIdentityAllTypes() {
    val protocMessage = AllTypesOuterClass.AllTypes.newBuilder().build()
    val wireMessageJava = AllTypesJ.Builder().build()
    val wireMessageKotlin = AllTypesK()

    val protocBytes = protocMessage.toByteArray()
    assertThat(AllTypesJ.ADAPTER.encode(wireMessageJava)).isEqualTo(protocBytes)
    assertThat(AllTypesJ.ADAPTER.decode(protocBytes)).isEqualTo(wireMessageJava)
    assertThat(AllTypesK.ADAPTER.encode(wireMessageKotlin)).isEqualTo(protocBytes)
    assertThat(AllTypesK.ADAPTER.decode(protocBytes)).isEqualTo(wireMessageKotlin)
  }

  @Test fun serializeDefaultAllTypesMoshi() {
    assertJsonEquals(DEFAULT_ALL_TYPES_JSON,
        moshi.adapter(AllTypesK::class.java).toJson(defaultAllTypesWireKotlin))
  }

  @Test fun deserializeDefaultAllTypesProtoc() {
    val allTypes = defaultAllTypesProtoc
    val jsonParser = JsonFormat.parser()
    val parsed = AllTypesOuterClass.AllTypes.newBuilder()
        .apply { jsonParser.merge(DEFAULT_ALL_TYPES_JSON, this) }
        .build()

    assertThat(parsed).isEqualTo(allTypes)
    assertThat(parsed.toString()).isEqualTo(allTypes.toString())
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(parsed), jsonPrinter.print(allTypes))
  }

  @Test fun deserializeDefaultAllTypesMoshi() {
    val allTypesAdapter: JsonAdapter<AllTypesK> = moshi.adapter(AllTypesK::class.java)

    val allTypes = defaultAllTypesWireKotlin
    val parsed = allTypesAdapter.fromJson(DEFAULT_ALL_TYPES_JSON)
    assertThat(parsed).isEqualTo(allTypes)
    assertThat(parsed.toString()).isEqualTo(allTypes.toString())
    assertJsonEquals(allTypesAdapter.toJson(parsed), allTypesAdapter.toJson(allTypes))
  }

  @Test fun serializeIdentityAllTypesProtoc() {
    val identityAllTypes = AllTypesOuterClass.AllTypes.newBuilder().build()

    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(IDENTITY_ALL_TYPES_JSON, jsonPrinter.print(identityAllTypes))
  }

  @Test fun serializeIdentityAllTypesMoshi() {
    // Moshi prints empty lists and empty arrays.
    val moshiJson = """{
      |  "mapInt32Int32": {},
      |  "mapStringEnum": {},
      |  "mapStringMessage": {},
      |  "mapStringString": {},
      |  "packBool": [],
      |  "packDouble": [],
      |  "packFixed32": [],
      |  "packFixed64": [],
      |  "packFloat": [],
      |  "packInt32": [],
      |  "packInt64": [],
      |  "packNestedEnum": [],
      |  "packSfixed32": [],
      |  "packSfixed64": [],
      |  "packSint32": [],
      |  "packSint64": [],
      |  "packUint32": [],
      |  "packUint64": [],
      |  "repBool": [],
      |  "repBytes": [],
      |  "repDouble": [],
      |  "repFixed32": [],
      |  "repFixed64": [],
      |  "repFloat": [],
      |  "repInt32": [],
      |  "repInt64": [],
      |  "repNestedEnum": [],
      |  "repNestedMessage": [],
      |  "repSfixed32": [],
      |  "repSfixed64": [],
      |  "repSint32": [],
      |  "repSint64": [],
      |  "repString": [],
      |  "repUint32": [],
      |  "repUint64": []
      |${IDENTITY_ALL_TYPES_JSON.substring(1)}""".trimMargin()

    val allTypes = AllTypesK()
    assertJsonEquals(moshiJson, moshi.adapter(AllTypesK::class.java).toJson(allTypes))
  }

  @Test fun serializeExplicitIdentityAllTypesProtoc() {
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(EXPLICIT_IDENTITY_ALL_TYPES_JSON,
        jsonPrinter.print(explicitIdentityAllTypesProtoc))
  }

  @Test fun serializeExplicitIdentityAllTypesMoshi() {
    // Moshi prints empty lists and empty arrays.
    val moshiJson = """{
      |  "mapStringString": {},
      |  "repBool": [],
      |  "repUint64": [],
      |  "repDouble": [],
      |  "repFixed64": [],
      |  "repSint64": [],
      |  "repNestedMessage": [],
      |  "repSfixed64": [],
      |  "repFloat": [],
      |  "repInt64": [],
      |  "packFixed32": [],
      |  "packInt32": [],
      |  "packSint32": [],
      |  "packUint32": [],
      |  "repNestedEnum": [],
      |  "repSfixed32": [],
      |${EXPLICIT_IDENTITY_ALL_TYPES_JSON.substring(1)}""".trimMargin()

    assertJsonEquals(moshiJson,
        moshi.adapter(AllTypesK::class.java).toJson(explicitIdentityAllTypesWireKotlin))
  }

  @Test fun deserializeIdentityAllTypesProtoc() {
    val identityAllTypes = AllTypesOuterClass.AllTypes.newBuilder().build()
    val jsonParser = JsonFormat.parser()
    val parsed = AllTypesOuterClass.AllTypes.newBuilder()
        .apply { jsonParser.merge(IDENTITY_ALL_TYPES_JSON, this) }
        .build()

    assertThat(parsed).isEqualTo(identityAllTypes)
    assertThat(parsed.toString()).isEqualTo(identityAllTypes.toString())
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(parsed), jsonPrinter.print(identityAllTypes))
  }

  @Test fun deserializeIdentityAllTypesMoshi() {
    val allTypesAdapter: JsonAdapter<AllTypesK> = moshi.adapter(AllTypesK::class.java)

    val allTypes = AllTypesK()
    val parsed = allTypesAdapter.fromJson(IDENTITY_ALL_TYPES_JSON)
    assertThat(parsed).isEqualTo(allTypes)
    assertThat(parsed.toString()).isEqualTo(allTypes.toString())
    assertJsonEquals(allTypesAdapter.toJson(parsed), allTypesAdapter.toJson(allTypes))
  }

  @Test fun deserializeExplicitIdentityAllTypesProtoc() {
    val jsonParser = JsonFormat.parser()
    val parsed = AllTypesOuterClass.AllTypes.newBuilder()
        .apply { jsonParser.merge(EXPLICIT_IDENTITY_ALL_TYPES_JSON, this) }
        .build()

    assertThat(parsed).isEqualTo(explicitIdentityAllTypesProtoc)
    assertThat(parsed.toString()).isEqualTo(explicitIdentityAllTypesProtoc.toString())
    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(parsed), jsonPrinter.print(explicitIdentityAllTypesProtoc))
  }

  @Test fun deserializeExplicitIdentityAllTypesMoshi() {
    val allTypesAdapter: JsonAdapter<AllTypesK> = moshi.adapter(AllTypesK::class.java)

    val parsed = allTypesAdapter.fromJson(EXPLICIT_IDENTITY_ALL_TYPES_JSON)
    assertThat(parsed).isEqualTo(explicitIdentityAllTypesWireKotlin)
    assertThat(parsed.toString()).isEqualTo(explicitIdentityAllTypesWireKotlin.toString())
    assertJsonEquals(allTypesAdapter.toJson(parsed),
        allTypesAdapter.toJson(explicitIdentityAllTypesWireKotlin))
  }

  @Test fun `int64s are encoded with quotes and decoded with either`() {
    val all64Adapter: JsonAdapter<All64K> = moshi.adapter(All64K::class.java)

    val signed = All64K(my_sint64 = 123, rep_sint64 = listOf(456))
    assertThat(all64Adapter.fromJson("""{"mySint64":"123", "repSint64": ["456"]}""")).isEqualTo(
        signed)
    assertThat(all64Adapter.fromJson("""{"mySint64":123, "repSint64": [456]}""")).isEqualTo(signed)
    assertThat(all64Adapter.fromJson("""{"mySint64":123.0, "repSint64": [456.0]}""")).isEqualTo(
        signed)

    val signedJson = all64Adapter.toJson(signed)
    assertThat(signedJson).contains(""""mySint64":"123"""")
    assertThat(signedJson).contains(""""repSint64":["456"]""")

    val unsigned = All64K(my_uint64 = 123, rep_uint64 = listOf(456))
    assertThat(all64Adapter.fromJson("""{"myUint64":"123", "repUint64": ["456"]}""")).isEqualTo(
        unsigned)
    assertThat(all64Adapter.fromJson("""{"myUint64":123, "repUint64": [456]}""")).isEqualTo(
        unsigned)
    assertThat(all64Adapter.fromJson("""{"myUint64":123.0, "repUint64": [456.0]}""")).isEqualTo(
        unsigned)

    val unsignedJson = all64Adapter.toJson(unsigned)
    assertThat(unsignedJson).contains(""""myUint64":"123"""")
    assertThat(unsignedJson).contains(""""repUint64":["456"]""")
  }

  @Test fun `protoc validation camel case json`() {
    val protocCamel = CamelCaseOuterClass.CamelCase.newBuilder()
        .setNestedMessage(CamelCaseOuterClass.CamelCase.NestedCamelCase.newBuilder().setOneInt32(1))
        .addAllRepInt32(listOf(1, 2))
        .setIDitItMyWAy("frank")
        .putMapInt32Int32(1, 2)
    assertJsonEquals(CAMEL_CASE_JSON, JsonFormat.printer().print(protocCamel))
  }

  @Test fun all64JsonProtocMaxValue() {
    val all64 = All64OuterClass.All64.newBuilder()
        .setMyInt64(Long.MAX_VALUE)
        .setMyUint64(Long.MAX_VALUE)
        .setMySint64(Long.MAX_VALUE)
        .setMyFixed64(Long.MAX_VALUE)
        .setMySfixed64(Long.MAX_VALUE)
        .addAllRepInt64(list(Long.MAX_VALUE))
        .addAllRepUint64(list(Long.MAX_VALUE))
        .addAllRepSint64(list(Long.MAX_VALUE))
        .addAllRepFixed64(list(Long.MAX_VALUE))
        .addAllRepSfixed64(list(Long.MAX_VALUE))
        .addAllPackInt64(list(Long.MAX_VALUE))
        .addAllPackUint64(list(Long.MAX_VALUE))
        .addAllPackSint64(list(Long.MAX_VALUE))
        .addAllPackFixed64(list(Long.MAX_VALUE))
        .addAllPackSfixed64(list(Long.MAX_VALUE))
        .setOneofInt64(Long.MAX_VALUE)
        .build()

    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(all64), ALL_64_JSON_MAX_VALUE)

    val jsonParser = JsonFormat.parser()
    val parsed = All64OuterClass.All64.newBuilder()
        .apply { jsonParser.merge(ALL_64_JSON_MAX_VALUE, this) }
        .build()
    assertThat(parsed).isEqualTo(all64)
  }

  @Test fun all64JsonMoshiMaxValue() {
    val all64 = All64K(
        my_int64 = Long.MAX_VALUE,
        my_uint64 = Long.MAX_VALUE,
        my_sint64 = Long.MAX_VALUE,
        my_fixed64 = Long.MAX_VALUE,
        my_sfixed64 = Long.MAX_VALUE,
        rep_int64 = list(Long.MAX_VALUE),
        rep_uint64 = list(Long.MAX_VALUE),
        rep_sint64 = list(Long.MAX_VALUE),
        rep_fixed64 = list(Long.MAX_VALUE),
        rep_sfixed64 = list(Long.MAX_VALUE),
        pack_int64 = list(Long.MAX_VALUE),
        pack_uint64 = list(Long.MAX_VALUE),
        pack_sint64 = list(Long.MAX_VALUE),
        pack_fixed64 = list(Long.MAX_VALUE),
        pack_sfixed64 = list(Long.MAX_VALUE),
        oneof_int64 = Long.MAX_VALUE
    )

    val moshi = Moshi.Builder()
        .add(WireJsonAdapterFactory())
        .build()
    val jsonAdapter = moshi.adapter(All64K::class.java).indent("  ")
    assertJsonEquals(jsonAdapter.toJson(all64), ALL_64_JSON_MAX_VALUE)
    assertThat(jsonAdapter.fromJson(ALL_64_JSON_MAX_VALUE)).isEqualTo(all64)
  }

  @Test fun all64JsonProtocMinValue() {
    val all64 = All64OuterClass.All64.newBuilder()
        .setMyInt64(Long.MIN_VALUE)
        .setMyUint64(Long.MIN_VALUE)
        .setMySint64(Long.MIN_VALUE)
        .setMyFixed64(Long.MIN_VALUE)
        .setMySfixed64(Long.MIN_VALUE)
        .addAllRepInt64(list(Long.MIN_VALUE))
        .addAllRepUint64(list(Long.MIN_VALUE))
        .addAllRepSint64(list(Long.MIN_VALUE))
        .addAllRepFixed64(list(Long.MIN_VALUE))
        .addAllRepSfixed64(list(Long.MIN_VALUE))
        .addAllPackInt64(list(Long.MIN_VALUE))
        .addAllPackUint64(list(Long.MIN_VALUE))
        .addAllPackSint64(list(Long.MIN_VALUE))
        .addAllPackFixed64(list(Long.MIN_VALUE))
        .addAllPackSfixed64(list(Long.MIN_VALUE))
        .setOneofInt64(Long.MIN_VALUE)
        .build()

    val jsonPrinter = JsonFormat.printer()
    assertJsonEquals(jsonPrinter.print(all64), ALL_64_JSON_MIN_VALUE)

    val jsonParser = JsonFormat.parser()
    val parsed = All64OuterClass.All64.newBuilder()
        .apply { jsonParser.merge(ALL_64_JSON_MIN_VALUE, this) }
        .build()
    assertThat(parsed).isEqualTo(all64)
  }

  @Test fun all64JsonMoshiMinValue() {
    val all64 = All64K(
        my_int64 = Long.MIN_VALUE,
        my_uint64 = Long.MIN_VALUE,
        my_sint64 = Long.MIN_VALUE,
        my_fixed64 = Long.MIN_VALUE,
        my_sfixed64 = Long.MIN_VALUE,
        rep_int64 = list(Long.MIN_VALUE),
        rep_uint64 = list(Long.MIN_VALUE),
        rep_sint64 = list(Long.MIN_VALUE),
        rep_fixed64 = list(Long.MIN_VALUE),
        rep_sfixed64 = list(Long.MIN_VALUE),
        pack_int64 = list(Long.MIN_VALUE),
        pack_uint64 = list(Long.MIN_VALUE),
        pack_sint64 = list(Long.MIN_VALUE),
        pack_fixed64 = list(Long.MIN_VALUE),
        pack_sfixed64 = list(Long.MIN_VALUE),
        oneof_int64 = Long.MIN_VALUE
    )

    val moshi = Moshi.Builder()
        .add(WireJsonAdapterFactory())
        .build()
    val jsonAdapter = moshi.adapter(All64K::class.java).indent("  ")
    assertJsonEquals(jsonAdapter.toJson(all64), ALL_64_JSON_MIN_VALUE)
    assertThat(jsonAdapter.fromJson(ALL_64_JSON_MIN_VALUE)).isEqualTo(all64)
  }

  @Test fun crashOnBigNumbersWhenIntIsSigned() {
    val json = """{"mySint64": "9223372036854775808"}"""

    val all64 = All64K()

    val moshi = Moshi.Builder()
        .add(WireJsonAdapterFactory())
        .build()
    val jsonAdapter = moshi.adapter(All64K::class.java)
    try {
      assertThat(jsonAdapter.fromJson(json)).isEqualTo(all64)
      fail()
    } catch (e: JsonDataException) {
      assertThat(e)
          .hasMessageContaining("decode failed: 9223372036854775808 at path \$.mySint64")
    }
  }

  @Test fun durationProto() {
    val googleMessage = PizzaOuterClass.PizzaDelivery.newBuilder()
        .setDeliveredWithinOrFree(Duration.newBuilder()
            .setSeconds(1_799)
            .setNanos(500_000_000)
            .build())
        .build()

    val wireMessage = PizzaDeliveryK(
        delivered_within_or_free = durationOfSeconds(1_799L, 500_000_000L)
    )

    val googleMessageBytes = googleMessage.toByteArray()
    assertThat(wireMessage.encode()).isEqualTo(googleMessageBytes)
    assertThat(PizzaDeliveryK.ADAPTER.decode(googleMessageBytes)).isEqualTo(wireMessage)
  }

  @Test fun instantProto() {
    val googleMessage = PizzaOuterClass.PizzaDelivery.newBuilder()
        .setOrderedAt(Timestamp.newBuilder()
            .setSeconds(-631152000000L) // 1950-01-01T00:00:00.250Z.
            .setNanos(250_000_000)
            .build())
        .build()

    val wireMessage = PizzaDeliveryK(
        ordered_at = ofEpochSecond(-631152000000L, 250_000_000L)
    )

    val googleMessageBytes = googleMessage.toByteArray()
    assertThat(wireMessage.encode()).isEqualTo(googleMessageBytes)
    assertThat(PizzaDeliveryK.ADAPTER.decode(googleMessageBytes)).isEqualTo(wireMessage)
  }

  @Test fun structProto() {
    val googleMessage = PizzaOuterClass.PizzaDelivery.newBuilder()
        .setLoyalty(Struct.newBuilder()
            .putFields("stamps", Value.newBuilder().setNumberValue(5.0).build())
            .putFields("members", Value.newBuilder().setListValue(
                ListValue.newBuilder()
                    .addValues(Value.newBuilder().setStringValue("Benoît").build())
                    .addValues(Value.newBuilder().setStringValue("Jesse").build())
                    .build())
                .build())
            .build())
        .build()

    val wireMessage = PizzaDeliveryK(
        loyalty = mapOf("stamps" to 5.0, "members" to listOf("Benoît", "Jesse"))
    )

    val googleMessageBytes = googleMessage.toByteArray()
    assertThat(wireMessage.encode()).isEqualTo(googleMessageBytes)
    assertThat(PizzaDeliveryK.ADAPTER.decode(googleMessageBytes)).isEqualTo(wireMessage)
  }

  @Test fun minusDoubleZero() {
    val protoc = AllTypesOuterClass.AllTypes.newBuilder()
        .setDouble(-0.0)
        .build()
    val wireKotlin = AllTypesK(squareup_proto3_kotlin_alltypes_double = -0.0)

    val protocByteArray = protoc.toByteArray()
    assertThat(AllTypesK.ADAPTER.encode(wireKotlin)).isEqualTo(protocByteArray)
    assertThat(AllTypesK.ADAPTER.decode(protocByteArray)).isEqualTo(wireKotlin)

    val protocJson = JsonFormat.printer().print(protoc)
    val wireKotlinJson = moshi.adapter(AllTypesK::class.java).toJson(wireKotlin)
    assertJsonEquals("{\"double\": -0.0}", protocJson)
    // We parse-check because Wire prints empty lists and empty maps while protoc doesn't.
    assertThat(wireKotlinJson).contains("\"double\":-0.0")

    val parsedWireKotlin = moshi.adapter(AllTypesK::class.java).fromJson(protocJson)
    assertThat(parsedWireKotlin).isEqualTo(wireKotlin)
  }

  companion object {
    private val moshi = Moshi.Builder()
        .add(WireJsonAdapterFactory())
        .build()

    private val gson = GsonBuilder().registerTypeAdapterFactory(WireTypeAdapterFactory())
        .disableHtmlEscaping()
        .create()

    private val defaultAllTypesProtoc = AllTypesOuterClass.AllTypes.newBuilder()
        .setInt32(111)
        .setUint32(112)
        .setSint32(113)
        .setFixed32(114)
        .setSfixed32(115)
        .setInt64(116L)
        .setUint64(117L)
        .setSint64(118L)
        .setFixed64(119L)
        .setSfixed64(120L)
        .setBool(true)
        .setFloat(122.0F)
        .setDouble(123.0)
        .setString("124")
        .setBytes(com.google.protobuf.ByteString.copyFrom(ByteString.of(123, 125).toByteArray()))
        .setNestedEnum(AllTypesOuterClass.AllTypes.NestedEnum.A)
        .setNestedMessage(AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().setA(999).build())
        .addAllRepInt32(list(111))
        .addAllRepUint32(list(112))
        .addAllRepSint32(list(113))
        .addAllRepFixed32(list(114))
        .addAllRepSfixed32(list(115))
        .addAllRepInt64(list(116L))
        .addAllRepUint64(list(117L))
        .addAllRepSint64(list(118L))
        .addAllRepFixed64(list(119L))
        .addAllRepSfixed64(list(120L))
        .addAllRepBool(list(true))
        .addAllRepFloat(list(122.0F))
        .addAllRepDouble(list(123.0))
        .addAllRepString(list("124"))
        .addAllRepBytes(
            list(com.google.protobuf.ByteString.copyFrom(ByteString.of(123, 125).toByteArray())))
        .addAllRepNestedEnum(list(AllTypesOuterClass.AllTypes.NestedEnum.A))
        .addAllRepNestedMessage(list(
            AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().setA(999).build()))
        .addAllPackInt32(list(111))
        .addAllPackUint32(list(112))
        .addAllPackSint32(list(113))
        .addAllPackFixed32(list(114))
        .addAllPackSfixed32(list(115))
        .addAllPackInt64(list(116L))
        .addAllPackUint64(list(117L))
        .addAllPackSint64(list(118L))
        .addAllPackFixed64(list(119L))
        .addAllPackSfixed64(list(120L))
        .addAllPackBool(list(true))
        .addAllPackFloat(list(122.0F))
        .addAllPackDouble(list(123.0))
        .addAllPackNestedEnum(list(AllTypesOuterClass.AllTypes.NestedEnum.A))
        .putMapInt32Int32(1, 2)
        .putMapStringString("key", "value")
        .putMapStringMessage("message",
            AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().setA(1).build())
        .putMapStringEnum("enum", AllTypesOuterClass.AllTypes.NestedEnum.A)
        .setOneofInt32(0)
        .build()

    private val defaultAllTypesWireKotlin = AllTypesK(
        squareup_proto3_kotlin_alltypes_int32 = 111,
        squareup_proto3_kotlin_alltypes_uint32 = 112,
        squareup_proto3_kotlin_alltypes_sint32 = 113,
        squareup_proto3_kotlin_alltypes_fixed32 = 114,
        squareup_proto3_kotlin_alltypes_sfixed32 = 115,
        squareup_proto3_kotlin_alltypes_int64 = 116L,
        squareup_proto3_kotlin_alltypes_uint64 = 117L,
        squareup_proto3_kotlin_alltypes_sint64 = 118L,
        squareup_proto3_kotlin_alltypes_fixed64 = 119L,
        squareup_proto3_kotlin_alltypes_sfixed64 = 120L,
        squareup_proto3_kotlin_alltypes_bool = true,
        squareup_proto3_kotlin_alltypes_float = 122.0F,
        squareup_proto3_kotlin_alltypes_double = 123.0,
        squareup_proto3_kotlin_alltypes_string = "124",
        squareup_proto3_kotlin_alltypes_bytes = ByteString.of(123, 125),
        nested_enum = AllTypesK.NestedEnum.A,
        nested_message = AllTypesK.NestedMessage(a = 999),
        rep_int32 = list(111),
        rep_uint32 = list(112),
        rep_sint32 = list(113),
        rep_fixed32 = list(114),
        rep_sfixed32 = list(115),
        rep_int64 = list(116L),
        rep_uint64 = list(117L),
        rep_sint64 = list(118L),
        rep_fixed64 = list(119L),
        rep_sfixed64 = list(120L),
        rep_bool = list(true),
        rep_float = list(122.0F),
        rep_double = list(123.0),
        rep_string = list("124"),
        rep_bytes = list(ByteString.of(123, 125)),
        rep_nested_enum = list(AllTypesK.NestedEnum.A),
        rep_nested_message = list(AllTypesK.NestedMessage(a = 999)),
        pack_int32 = list(111),
        pack_uint32 = list(112),
        pack_sint32 = list(113),
        pack_fixed32 = list(114),
        pack_sfixed32 = list(115),
        pack_int64 = list(116L),
        pack_uint64 = list(117L),
        pack_sint64 = list(118L),
        pack_fixed64 = list(119L),
        pack_sfixed64 = list(120L),
        pack_bool = list(true),
        pack_float = list(122.0F),
        pack_double = list(123.0),
        pack_nested_enum = list(AllTypesK.NestedEnum.A),
        map_int32_int32 = mapOf(1 to 2),
        map_string_string = mapOf("key" to "value"),
        map_string_message = mapOf("message" to AllTypesK.NestedMessage(1)),
        map_string_enum = mapOf("enum" to AllTypesK.NestedEnum.A),
        oneof_int32 = 0
    )

    private val defaultAllTypesWireJava = AllTypesJ.Builder()
        .int32(111)
        .uint32(112)
        .sint32(113)
        .fixed32(114)
        .sfixed32(115)
        .int64(116L)
        .uint64(117L)
        .sint64(118L)
        .fixed64(119L)
        .sfixed64(120L)
        .bool(true)
        .float_(122.0F)
        .double_(123.0)
        .string("124")
        .bytes(ByteString.of(123, 125))
        .nested_enum(AllTypesJ.NestedEnum.A)
        .nested_message(AllTypesJ.NestedMessage(999))
        .rep_int32(list(111))
        .rep_uint32(list(112))
        .rep_sint32(list(113))
        .rep_fixed32(list(114))
        .rep_sfixed32(list(115))
        .rep_int64(list(116L))
        .rep_uint64(list(117L))
        .rep_sint64(list(118L))
        .rep_fixed64(list(119L))
        .rep_sfixed64(list(120L))
        .rep_bool(list(true))
        .rep_float(list(122.0F))
        .rep_double(list(123.0))
        .rep_string(list("124"))
        .rep_bytes(list(ByteString.of(123, 125)))
        .rep_nested_enum(list(AllTypesJ.NestedEnum.A))
        .rep_nested_message(list(AllTypesJ.NestedMessage(999)))
        .pack_int32(list(111))
        .pack_uint32(list(112))
        .pack_sint32(list(113))
        .pack_fixed32(list(114))
        .pack_sfixed32(list(115))
        .pack_int64(list(116L))
        .pack_uint64(list(117L))
        .pack_sint64(list(118L))
        .pack_fixed64(list(119L))
        .pack_sfixed64(list(120L))
        .pack_bool(list(true))
        .pack_float(list(122.0F))
        .pack_double(list(123.0))
        .pack_nested_enum(list(AllTypesJ.NestedEnum.A))
        .map_int32_int32(mapOf(1 to 2))
        .map_string_string(mapOf("key" to "value"))
        .map_string_message(mapOf("message" to AllTypesJ.NestedMessage(1)))
        .map_string_enum(mapOf("enum" to AllTypesJ.NestedEnum.A))
        .oneof_int32(0)
        .build()

    private val CAMEL_CASE_JSON =
        File("../wire-library/wire-tests/src/commonTest/shared/json", "camel_case_proto3.json")
            .source().use { it.buffer().readUtf8() }

    private val DEFAULT_ALL_TYPES_JSON =
        File("../wire-library/wire-tests/src/commonTest/shared/json", "all_types_proto3.json")
            .source().use { it.buffer().readUtf8() }

    private val ALL_64_JSON_MIN_VALUE =
        File("../wire-library/wire-tests/src/commonTest/shared/json", "all_64_min_proto3.json")
            .source().use { it.buffer().readUtf8() }

    private val ALL_64_JSON_MAX_VALUE =
        File("../wire-library/wire-tests/src/commonTest/shared/json", "all_64_max_proto3.json")
            .source().use { it.buffer().readUtf8() }

    private val IDENTITY_ALL_TYPES_JSON =
        File("../wire-library/wire-tests/src/commonTest/shared/json", "all_types_identity_proto3.json")
            .source().use { it.buffer().readUtf8() }

    /** This is used to confirmed identity values are emitted in lists and maps. */
    private val EXPLICIT_IDENTITY_ALL_TYPES_JSON =
        File("../wire-library/wire-tests/src/commonTest/shared/json", "all_types_explicit_identity_proto3.json")
            .source().use { it.buffer().readUtf8() }

    private val explicitIdentityAllTypesWireKotlin = AllTypesK(
        squareup_proto3_kotlin_alltypes_int32 = 0,
        squareup_proto3_kotlin_alltypes_uint32 = 0,
        squareup_proto3_kotlin_alltypes_sint32 = 0,
        squareup_proto3_kotlin_alltypes_fixed32 = 0,
        squareup_proto3_kotlin_alltypes_sfixed32 = 0,
        squareup_proto3_kotlin_alltypes_int64 = 0L,
        squareup_proto3_kotlin_alltypes_uint64 = 0L,
        squareup_proto3_kotlin_alltypes_sint64 = 0L,
        squareup_proto3_kotlin_alltypes_fixed64 = 0L,
        squareup_proto3_kotlin_alltypes_sfixed64 = 0L,
        squareup_proto3_kotlin_alltypes_bool = false,
        squareup_proto3_kotlin_alltypes_float = 0F,
        squareup_proto3_kotlin_alltypes_double = 0.0,
        squareup_proto3_kotlin_alltypes_string = "",
        squareup_proto3_kotlin_alltypes_bytes = ByteString.EMPTY,
        nested_enum = AllTypesK.NestedEnum.UNKNOWN,
        nested_message = AllTypesK.NestedMessage(a = 0),
        rep_int32 = list(0),
        rep_uint32 = list(0),
        rep_sint32 = list(0),
        rep_fixed32 = list(0),
        rep_sfixed32 = emptyList(),
        rep_int64 = emptyList(),
        rep_uint64 = emptyList(),
        rep_sint64 = emptyList(),
        rep_fixed64 = emptyList(),
        rep_sfixed64 = emptyList(),
        rep_bool = emptyList(),
        rep_float = emptyList(),
        rep_double = emptyList(),
        rep_string = list(""),
        rep_bytes = list(ByteString.EMPTY),
        rep_nested_enum = emptyList(),
        rep_nested_message = emptyList(),
        pack_int32 = emptyList(),
        pack_uint32 = emptyList(),
        pack_sint32 = emptyList(),
        pack_fixed32 = emptyList(),
        pack_sfixed32 = list(0),
        pack_int64 = list(0L),
        pack_uint64 = list(0L),
        pack_sint64 = list(0L),
        pack_fixed64 = list(0L),
        pack_sfixed64 = list(0L),
        pack_bool = list(false),
        pack_float = list(0F),
        pack_double = list(0.0),
        pack_nested_enum = list(AllTypesK.NestedEnum.UNKNOWN),
        map_int32_int32 = mapOf(0 to 0),
        map_string_message = mapOf("" to AllTypesK.NestedMessage()),
        map_string_enum = mapOf("" to AllTypesK.NestedEnum.UNKNOWN),
        oneof_int32 = 0
    )

    private val explicitIdentityAllTypesWireJava = AllTypesJ.Builder()
        .int32(0)
        .uint32(0)
        .sint32(0)
        .fixed32(0)
        .sfixed32(0)
        .int64(0L)
        .uint64(0L)
        .sint64(0L)
        .fixed64(0L)
        .sfixed64(0L)
        .bool(false)
        .float_(0F)
        .double_(0.0)
        .string("")
        .bytes(ByteString.EMPTY)
        .nested_enum(AllTypesJ.NestedEnum.UNKNOWN)
        .nested_message(AllTypesJ.NestedMessage(0))
        .rep_int32(list(0))
        .rep_uint32(list(0))
        .rep_sint32(list(0))
        .rep_fixed32(list(0))
        .rep_sfixed32(emptyList())
        .rep_int64(emptyList())
        .rep_uint64(emptyList())
        .rep_sint64(emptyList())
        .rep_fixed64(emptyList())
        .rep_sfixed64(emptyList())
        .rep_bool(emptyList())
        .rep_float(emptyList())
        .rep_double(emptyList())
        .rep_string(list(""))
        .rep_bytes(list(ByteString.EMPTY))
        .rep_nested_enum(emptyList())
        .rep_nested_message(emptyList())
        .pack_int32(emptyList())
        .pack_uint32(emptyList())
        .pack_sint32(emptyList())
        .pack_fixed32(emptyList())
        .pack_sfixed32(list(0))
        .pack_int64(list(0L))
        .pack_uint64(list(0L))
        .pack_sint64(list(0L))
        .pack_fixed64(list(0L))
        .pack_sfixed64(list(0L))
        .pack_bool(list(false))
        .pack_float(list(0F))
        .pack_double(list(0.0))
        .pack_nested_enum(list(AllTypesJ.NestedEnum.UNKNOWN))
        .map_int32_int32(mapOf(0 to 0))
        .map_string_message(mapOf("" to AllTypesJ.NestedMessage(null)))
        .map_string_enum(mapOf("" to AllTypesJ.NestedEnum.UNKNOWN))
        .oneof_int32(0)
        .build()

    private val explicitIdentityAllTypesProtoc = AllTypesOuterClass.AllTypes.newBuilder()
        .setInt32(0)
        .setUint32(0)
        .setSint32(0)
        .setFixed32(0)
        .setSfixed32(0)
        .setInt64(0L)
        .setUint64(0L)
        .setSint64(0L)
        .setFixed64(0L)
        .setSfixed64(0L)
        .setBool(false)
        .setFloat(0F)
        .setDouble(0.0)
        .setString("")
        .setBytes(com.google.protobuf.ByteString.copyFrom(ByteString.EMPTY.toByteArray()))
        .setNestedEnum(AllTypesOuterClass.AllTypes.NestedEnum.UNKNOWN)
        .setNestedMessage(AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().setA(0).build())
        .addAllRepInt32(list(0))
        .addAllRepUint32(list(0))
        .addAllRepSint32(list(0))
        .addAllRepFixed32(list(0))
        .addAllRepSfixed32(emptyList())
        .addAllRepInt64(emptyList())
        .addAllRepUint64(emptyList())
        .addAllRepSint64(emptyList())
        .addAllRepFixed64(emptyList())
        .addAllRepSfixed64(emptyList())
        .addAllRepBool(emptyList())
        .addAllRepFloat(emptyList())
        .addAllRepDouble(emptyList())
        .addAllRepString(list(""))
        .addAllRepBytes(
            list(com.google.protobuf.ByteString.copyFrom(ByteString.EMPTY.toByteArray())))
        .addAllRepNestedEnum(emptyList())
        .addAllRepNestedMessage(emptyList())
        .addAllPackInt32(emptyList())
        .addAllPackUint32(emptyList())
        .addAllPackSint32(emptyList())
        .addAllPackFixed32(emptyList())
        .addAllPackSfixed32(list(0))
        .addAllPackInt64(list(0L))
        .addAllPackUint64(list(0L))
        .addAllPackSint64(list(0L))
        .addAllPackFixed64(list(0L))
        .addAllPackSfixed64(list(0L))
        .addAllPackBool(list(false))
        .addAllPackFloat(list(0F))
        .addAllPackDouble(list(0.0))
        .addAllPackNestedEnum(list(
            AllTypesOuterClass.AllTypes.NestedEnum.UNKNOWN))
        .putMapInt32Int32(0, 0)
        .putMapStringMessage("", AllTypesOuterClass.AllTypes.NestedMessage.newBuilder().build())
        .putMapStringEnum("", AllTypesOuterClass.AllTypes.NestedEnum.UNKNOWN)
        .setOneofInt32(0)
        .build()

    private fun <T : kotlin.Any> list(t: T): List<T> {
      return listOf(t, t)
    }
  }
}
