/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.basics.market;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;

/**
 * A set of market data where an item has been added or overridden.
 * <p>
 * This decorates an underlying instance to add or replace a single identifier.
 */
@BeanDefinition(builderScope = "private", constructorScope = "package")
final class ExtendedMarketData<T>
    implements MarketData, ImmutableBean, Serializable {

  /**
   * The additional market data identifier.
   */
  @PropertyDefinition(validate = "notNull")
  private final MarketDataKey<T> key;
  /**
   * The additional market data value.
   */
  @PropertyDefinition(validate = "notNull")
  private final T value;
  /**
   * The underlying market data.
   */
  @PropertyDefinition(validate = "notNull")
  private final MarketData underlying;

  //-------------------------------------------------------------------------
  /**
   * Obtains an instance that decorates the underlying market data.
   * <p>
   * The specified identifier can be queried in the result, returning the specified value.
   * All other identifiers are queried in the underlying market data.
   *
   * @param key  the additional market data identifier
   * @param value  the additional market data value
   * @param underlying  the underlying market data
   * @return a market data instance that decorates the original adding/overriding the specified identifier
   */
  public static <T> ExtendedMarketData<T> of(MarketDataKey<T> key, T value, MarketData underlying) {
    return new ExtendedMarketData<T>(key, value, underlying);
  }

  //-------------------------------------------------------------------------
  @Override
  public LocalDate getValuationDate() {
    return underlying.getValuationDate();
  }

  @Override
  public boolean containsValue(MarketDataKey<?> key) {
    if (this.key.equals(key)) {
      return true;
    }
    return underlying.containsValue(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> Optional<R> findValue(MarketDataKey<R> key) {
    if (this.key.equals(key)) {
      return Optional.of((R) value);
    }
    return underlying.findValue(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R getValue(MarketDataKey<R> key) {
    if (this.key.equals(key)) {
      return (R) value;
    }
    return underlying.getValue(key);
  }

  @Override
  public LocalDateDoubleTimeSeries getTimeSeries(ObservableKey key) {
    return underlying.getTimeSeries(key);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ExtendedMarketData}.
   * @return the meta-bean, not null
   */
  @SuppressWarnings("rawtypes")
  public static ExtendedMarketData.Meta meta() {
    return ExtendedMarketData.Meta.INSTANCE;
  }

  /**
   * The meta-bean for {@code ExtendedMarketData}.
   * @param <R>  the bean's generic type
   * @param cls  the bean's generic type
   * @return the meta-bean, not null
   */
  @SuppressWarnings("unchecked")
  public static <R> ExtendedMarketData.Meta<R> metaExtendedMarketData(Class<R> cls) {
    return ExtendedMarketData.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(ExtendedMarketData.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Creates an instance.
   * @param key  the value of the property, not null
   * @param value  the value of the property, not null
   * @param underlying  the value of the property, not null
   */
  ExtendedMarketData(
      MarketDataKey<T> key,
      T value,
      MarketData underlying) {
    JodaBeanUtils.notNull(key, "key");
    JodaBeanUtils.notNull(value, "value");
    JodaBeanUtils.notNull(underlying, "underlying");
    this.key = key;
    this.value = value;
    this.underlying = underlying;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ExtendedMarketData.Meta<T> metaBean() {
    return ExtendedMarketData.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the additional market data identifier.
   * @return the value of the property, not null
   */
  public MarketDataKey<T> getKey() {
    return key;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the additional market data value.
   * @return the value of the property, not null
   */
  public T getValue() {
    return value;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the underlying market data.
   * @return the value of the property, not null
   */
  public MarketData getUnderlying() {
    return underlying;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      ExtendedMarketData<?> other = (ExtendedMarketData<?>) obj;
      return JodaBeanUtils.equal(key, other.key) &&
          JodaBeanUtils.equal(value, other.value) &&
          JodaBeanUtils.equal(underlying, other.underlying);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(key);
    hash = hash * 31 + JodaBeanUtils.hashCode(value);
    hash = hash * 31 + JodaBeanUtils.hashCode(underlying);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(128);
    buf.append("ExtendedMarketData{");
    buf.append("key").append('=').append(key).append(',').append(' ');
    buf.append("value").append('=').append(value).append(',').append(' ');
    buf.append("underlying").append('=').append(JodaBeanUtils.toString(underlying));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ExtendedMarketData}.
   * @param <T>  the type
   */
  public static final class Meta<T> extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    @SuppressWarnings("rawtypes")
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code key} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<MarketDataKey<T>> key = DirectMetaProperty.ofImmutable(
        this, "key", ExtendedMarketData.class, (Class) MarketDataKey.class);
    /**
     * The meta-property for the {@code value} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<T> value = (DirectMetaProperty) DirectMetaProperty.ofImmutable(
        this, "value", ExtendedMarketData.class, Object.class);
    /**
     * The meta-property for the {@code underlying} property.
     */
    private final MetaProperty<MarketData> underlying = DirectMetaProperty.ofImmutable(
        this, "underlying", ExtendedMarketData.class, MarketData.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "key",
        "value",
        "underlying");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 106079:  // key
          return key;
        case 111972721:  // value
          return value;
        case -1770633379:  // underlying
          return underlying;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends ExtendedMarketData<T>> builder() {
      return new ExtendedMarketData.Builder<T>();
    }

    @SuppressWarnings({"unchecked", "rawtypes" })
    @Override
    public Class<? extends ExtendedMarketData<T>> beanType() {
      return (Class) ExtendedMarketData.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code key} property.
     * @return the meta-property, not null
     */
    public MetaProperty<MarketDataKey<T>> key() {
      return key;
    }

    /**
     * The meta-property for the {@code value} property.
     * @return the meta-property, not null
     */
    public MetaProperty<T> value() {
      return value;
    }

    /**
     * The meta-property for the {@code underlying} property.
     * @return the meta-property, not null
     */
    public MetaProperty<MarketData> underlying() {
      return underlying;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 106079:  // key
          return ((ExtendedMarketData<?>) bean).getKey();
        case 111972721:  // value
          return ((ExtendedMarketData<?>) bean).getValue();
        case -1770633379:  // underlying
          return ((ExtendedMarketData<?>) bean).getUnderlying();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code ExtendedMarketData}.
   * @param <T>  the type
   */
  private static final class Builder<T> extends DirectFieldsBeanBuilder<ExtendedMarketData<T>> {

    private MarketDataKey<T> key;
    private T value;
    private MarketData underlying;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 106079:  // key
          return key;
        case 111972721:  // value
          return value;
        case -1770633379:  // underlying
          return underlying;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder<T> set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 106079:  // key
          this.key = (MarketDataKey<T>) newValue;
          break;
        case 111972721:  // value
          this.value = (T) newValue;
          break;
        case -1770633379:  // underlying
          this.underlying = (MarketData) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder<T> set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder<T> setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder<T> setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder<T> setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public ExtendedMarketData<T> build() {
      return new ExtendedMarketData<T>(
          key,
          value,
          underlying);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(128);
      buf.append("ExtendedMarketData.Builder{");
      buf.append("key").append('=').append(JodaBeanUtils.toString(key)).append(',').append(' ');
      buf.append("value").append('=').append(JodaBeanUtils.toString(value)).append(',').append(' ');
      buf.append("underlying").append('=').append(JodaBeanUtils.toString(underlying));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}