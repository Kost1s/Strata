/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.math.impl.interpolation;

import java.io.Serializable;
import java.util.Set;

import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.light.LightMetaBean;

import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.math.impl.MathException;
import com.opengamma.strata.math.impl.interpolation.data.ArrayInterpolator1DDataBundle;
import com.opengamma.strata.math.impl.interpolation.data.Interpolator1DCubicSplineDataBundle;
import com.opengamma.strata.math.impl.interpolation.data.Interpolator1DDataBundle;

/**
 * 
 */
@BeanDefinition(style = "light", constructorScope = "public")
public final class NaturalCubicSplineInterpolator1D
    extends Interpolator1D
    implements CurveInterpolator, ImmutableBean, Serializable {

  /** The name of the interpolator. */
  private static final String NAME = "NaturalCubicSpline";

  @PropertyDefinition
  private final double eps;

  /**
   * Creates an instance.
   */
  public NaturalCubicSplineInterpolator1D() {
    this.eps = 1e-12;
  }

  //-------------------------------------------------------------------------
  @Override
  public Double interpolate(Interpolator1DDataBundle data, Double value) {
    ArgChecker.notNull(value, "value");
    ArgChecker.notNull(data, "data bundle");
    ArgChecker.isTrue(data instanceof Interpolator1DCubicSplineDataBundle);
    Interpolator1DCubicSplineDataBundle splineData = (Interpolator1DCubicSplineDataBundle) data;
    int low = data.getLowerBoundIndex(value);
    int high = low + 1;
    int n = data.size() - 1;
    double[] xData = data.getKeys();
    double[] yData = data.getValues();
    if (data.getLowerBoundIndex(value) == n) {
      return yData[n];
    }
    double delta = xData[high] - xData[low];
    if (Math.abs(delta) < eps) {
      throw new MathException("x data points were not distinct");
    }
    double a = (xData[high] - value) / delta;
    double b = (value - xData[low]) / delta;
    double[] y2 = splineData.getSecondDerivatives();
    return a * yData[low] + b * yData[high] + (a * (a * a - 1) * y2[low] + b * (b * b - 1) * y2[high]) * delta * delta / 6.;
  }

  @Override
  public double firstDerivative(Interpolator1DDataBundle data, Double value) {
    ArgChecker.notNull(value, "value");
    ArgChecker.notNull(data, "data bundle");
    ArgChecker.isTrue(data instanceof Interpolator1DCubicSplineDataBundle);
    Interpolator1DCubicSplineDataBundle splineData = (Interpolator1DCubicSplineDataBundle) data;
    int low = data.getLowerBoundIndex(value);
    int high = low + 1;
    int n = data.size() - 1;
    double[] xData = data.getKeys();
    double[] yData = data.getValues();
    if (low == n) {
      low = n - 1;
      high = n;
    }
    double delta = xData[high] - xData[low];
    if (Math.abs(delta) < eps) {
      throw new MathException("x data points were not distinct");
    }
    double a = (xData[high] - value) / delta;
    double b = (value - xData[low]) / delta;
    double[] y2 = splineData.getSecondDerivatives();
    return (yData[high] - yData[low]) / delta + ((-3. * a * a + 1.) * y2[low] + (3. * b * b - 1.) * y2[high]) * delta / 6.;
  }

  @Override
  public double[] getNodeSensitivitiesForValue(Interpolator1DDataBundle data, Double value) {
    ArgChecker.notNull(data, "data");
    ArgChecker.isTrue(data instanceof Interpolator1DCubicSplineDataBundle);
    Interpolator1DCubicSplineDataBundle cubicData = (Interpolator1DCubicSplineDataBundle) data;
    int n = cubicData.size();
    double[] result = new double[n];
    if (cubicData.getLowerBoundIndex(value) == n - 1) {
      result[n - 1] = 1.0;
      return result;
    }
    double[] xData = cubicData.getKeys();
    int low = cubicData.getLowerBoundIndex(value);
    int high = low + 1;
    double delta = xData[high] - xData[low];
    double a = (xData[high] - value) / delta;
    double b = (value - xData[low]) / delta;
    double c = a * (a * a - 1) * delta * delta / 6.;
    double d = b * (b * b - 1) * delta * delta / 6.;
    double[][] y2Sensitivities = cubicData.getSecondDerivativesSensitivities();
    for (int i = 0; i < n; i++) {
      result[i] = c * y2Sensitivities[low][i] + d * y2Sensitivities[high][i];
    }
    result[low] += a;
    result[high] += b;
    return result;
  }

  @Override
  public Interpolator1DDataBundle getDataBundle(double[] x, double[] y) {
    return new Interpolator1DCubicSplineDataBundle(new ArrayInterpolator1DDataBundle(x, y));
  }

  @Override
  public Interpolator1DDataBundle getDataBundleFromSortedArrays(double[] x, double[] y) {
    return new Interpolator1DCubicSplineDataBundle(new ArrayInterpolator1DDataBundle(x, y, true));
  }

  @Override
  public String getName() {
    return NAME;
  }
  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code NaturalCubicSplineInterpolator1D}.
   */
  private static MetaBean META_BEAN = LightMetaBean.of(NaturalCubicSplineInterpolator1D.class);

  /**
   * The meta-bean for {@code NaturalCubicSplineInterpolator1D}.
   * @return the meta-bean, not null
   */
  public static MetaBean meta() {
    return META_BEAN;
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Creates an instance.
   * @param eps  the value of the property
   */
  public NaturalCubicSplineInterpolator1D(
      double eps) {
    this.eps = eps;
  }

  @Override
  public MetaBean metaBean() {
    return META_BEAN;
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
   * Gets the eps.
   * @return the value of the property
   */
  public double getEps() {
    return eps;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      NaturalCubicSplineInterpolator1D other = (NaturalCubicSplineInterpolator1D) obj;
      return JodaBeanUtils.equal(getEps(), other.getEps());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getEps());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(64);
    buf.append("NaturalCubicSplineInterpolator1D{");
    buf.append("eps").append('=').append(JodaBeanUtils.toString(getEps()));
    buf.append('}');
    return buf.toString();
  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}