package com.opengamma.strata.pricer.credit.cds;

import static com.opengamma.strata.math.impl.util.Epsilon.epsilon;
import static com.opengamma.strata.math.impl.util.Epsilon.epsilonP;
import static com.opengamma.strata.math.impl.util.Epsilon.epsilonPP;

import java.time.LocalDate;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.math.impl.util.Epsilon;
import com.opengamma.strata.product.credit.cds.CreditCouponPaymentPeriod;
import com.opengamma.strata.product.credit.cds.ResolvedCds;

public class IsdaCdsProductPricer {

  /**
   * Default implementation.
   */
  public static final IsdaCdsProductPricer DEFAULT = new IsdaCdsProductPricer(AccrualOnDefaultFormulae.ORIGINAL_ISDA);

  /**
   * The formula
   */
  private final AccrualOnDefaultFormulae formula;

  /**
   * The omega parameter.
   */
  private final double omega;

  /**
   * Which formula to use for the accrued on default calculation.  
   * @param formula Options are the formula given in the ISDA model (version 1.8.2 and lower); the proposed fix by Markit (given as a comment in  
   * version 1.8.2, or the mathematically correct formula 
   */
  public IsdaCdsProductPricer(AccrualOnDefaultFormulae formula) {
    this.formula = ArgChecker.notNull(formula, "formula");
    if (formula == AccrualOnDefaultFormulae.ORIGINAL_ISDA) {
      omega = 1d / 730d;
    } else {
      omega = 0d;
    }
  }

  //-------------------------------------------------------------------------
  public CurrencyAmount presentValue(
      ResolvedCds cds,
      CreditRatesProvider ratesProvider,
      LocalDate referenceDate,
      PriceType priceType,
      ReferenceData refData) {

    double price = price(cds, ratesProvider, referenceDate, priceType, refData);
    return CurrencyAmount.of(cds.getCurrency(), cds.getBuySell().normalize(cds.getNotional()) * price);
  }

  public double price(
      ResolvedCds cds,
      CreditRatesProvider ratesProvider,
      LocalDate referenceDate,
      PriceType priceType,
      ReferenceData refData) {

    ArgChecker.notNull(cds, "cds");
    ArgChecker.notNull(ratesProvider, "ratesProvider");
    ArgChecker.notNull(referenceDate, "referenceDate");
    ArgChecker.notNull(refData, "refData");
    if (!cds.getProtectionEndDate().isAfter(ratesProvider.getValuationDate())) { //short cut already expired CDSs
      return 0d;
    }
    LocalDate stepinDate = cds.getStepinDateOffset().adjust(ratesProvider.getValuationDate(), refData);
    LocalDate effectiveStartDate = cds.getEffectiveStartDate(stepinDate);
    double recoveryRate = recoveryRate(cds, ratesProvider);
    Pair<CreditDiscountFactors, LegalEntitySurvivalProbabilities> rates = reduceDiscountFactors(cds, ratesProvider);
    double protectionLeg =
        protectionLeg(cds, rates.getFirst(), rates.getSecond(), referenceDate, effectiveStartDate, recoveryRate);
    double rpv01 = riskyAnnuity(
        cds, rates.getFirst(), rates.getSecond(), referenceDate, stepinDate, effectiveStartDate, priceType);
    double price = protectionLeg - rpv01 * cds.getFixedRate();

    return price;
  }

  public double parSpread(
      ResolvedCds cds,
      CreditRatesProvider ratesProvider,
      LocalDate referenceDate,
      ReferenceData refData) {

    ArgChecker.notNull(cds, "cds");
    ArgChecker.notNull(ratesProvider, "ratesProvider");
    ArgChecker.notNull(referenceDate, "referenceDate");
    ArgChecker.notNull(refData, "refData");
    ArgChecker.isTrue(cds.getProtectionEndDate().isAfter(ratesProvider.getValuationDate()), "CDS already expired");
    LocalDate stepinDate = cds.getStepinDateOffset().adjust(ratesProvider.getValuationDate(), refData);
    LocalDate effectiveStartDate = cds.getEffectiveStartDate(stepinDate);
    double recoveryRate = recoveryRate(cds, ratesProvider);
    Pair<CreditDiscountFactors, LegalEntitySurvivalProbabilities> rates = reduceDiscountFactors(cds, ratesProvider);
    double protectionLeg =
        protectionLeg(cds, rates.getFirst(), rates.getSecond(), referenceDate, effectiveStartDate, recoveryRate);
    double riskyAnnuity =
        riskyAnnuity(cds, rates.getFirst(), rates.getSecond(), referenceDate, stepinDate, effectiveStartDate, PriceType.CLEAN);
    double parSpread = protectionLeg / riskyAnnuity;

    return parSpread;
  }

  //-------------------------------------------------------------------------
  public CurrencyAmount rpv01(
      ResolvedCds cds,
      CreditRatesProvider ratesProvider,
      LocalDate referenceDate,
      PriceType priceType,
      ReferenceData refData) {

    double riskyAnnuity = riskyAnnuity(cds, ratesProvider, referenceDate, priceType, refData);
    return CurrencyAmount.of(cds.getCurrency(), cds.getBuySell().normalize(cds.getNotional()) * riskyAnnuity);
  }

  public CurrencyAmount recovery01(
      ResolvedCds cds,
      CreditRatesProvider ratesProvider,
      LocalDate referenceDate,
      ReferenceData refData) {

    ArgChecker.notNull(cds, "cds");
    ArgChecker.notNull(ratesProvider, "ratesProvider");
    ArgChecker.notNull(referenceDate, "referenceDate");
    ArgChecker.notNull(refData, "refData");
    ArgChecker.isFalse(cds.getProtectionEndDate().isBefore(ratesProvider.getValuationDate()), "CDS already expired");
    LocalDate stepinDate = cds.getStepinDateOffset().adjust(ratesProvider.getValuationDate(), refData);
    LocalDate effectiveStartDate = cds.getEffectiveStartDate(stepinDate);
    recoveryRate(cds, ratesProvider); // for validation
    Pair<CreditDiscountFactors, LegalEntitySurvivalProbabilities> rates = reduceDiscountFactors(cds, ratesProvider);
    double protectionFull =
        protectionFull(cds, rates.getFirst(), rates.getSecond(), referenceDate, effectiveStartDate);

    return CurrencyAmount.of(cds.getCurrency(), -cds.getBuySell().normalize(cds.getNotional()) * protectionFull);
  }

  public PointSensitivityBuilder presentValueSensitivity(
      ResolvedCds cds,
      CreditRatesProvider ratesProvider,
      LocalDate referenceDate,
      ReferenceData refData) {

    ArgChecker.notNull(cds, "cds");
    ArgChecker.notNull(ratesProvider, "ratesProvider");
    ArgChecker.notNull(referenceDate, "referenceDate");
    ArgChecker.notNull(refData, "refData");
    if (!cds.getProtectionEndDate().isAfter(ratesProvider.getValuationDate())) { //short cut already expired CDSs
      return PointSensitivityBuilder.none();
    }
    LocalDate stepinDate = cds.getStepinDateOffset().adjust(ratesProvider.getValuationDate(), refData);
    LocalDate effectiveStartDate = cds.getEffectiveStartDate(stepinDate);
    double recoveryRate = recoveryRate(cds, ratesProvider);
    Pair<CreditDiscountFactors, LegalEntitySurvivalProbabilities> rates = reduceDiscountFactors(cds, ratesProvider);

    double signedNotional = cds.getBuySell().normalize(cds.getNotional());
    PointSensitivityBuilder protectionLegSensi =
        protectionLegSensitivity(cds, rates.getFirst(), rates.getSecond(), referenceDate, effectiveStartDate, recoveryRate)
            .multipliedBy(signedNotional);
    PointSensitivityBuilder riskyAnnuitySensi = riskyAnnuitySensitivity(
        cds, rates.getFirst(), rates.getSecond(), referenceDate, stepinDate, effectiveStartDate)
            .multipliedBy(-cds.getFixedRate() * signedNotional);

    return protectionLegSensi.combinedWith(riskyAnnuitySensi);
  }

//  public PointSensitivityBuilder protectionPvSensitivity(
//      ResolvedCds cds,
//      CreditRatesProvider ratesProvider,
//      LocalDate referenceDate,
//      PriceType priceType,
//      ReferenceData refData) {
//
//    ArgChecker.notNull(cds, "cds");
//    ArgChecker.notNull(ratesProvider, "ratesProvider");
//    ArgChecker.notNull(referenceDate, "referenceDate");
//    ArgChecker.notNull(refData, "refData");
//    if (!cds.getProtectionEndDate().isAfter(ratesProvider.getValuationDate())) { //short cut already expired CDSs
//      return PointSensitivityBuilder.none();
//    }
//    LocalDate stepinDate = cds.getStepinDateOffset().adjust(ratesProvider.getValuationDate(), refData);
//    LocalDate effectiveStartDate = cds.getEffectiveStartDate(stepinDate);
//    double recoveryRate = recoveryRate(cds, ratesProvider);
//    Pair<CreditDiscountFactors, LegalEntitySurvivalProbabilities> rates = reduceDiscountFactors(cds, ratesProvider);
//
//    double signedNotional = cds.getBuySell().normalize(cds.getNotional());
//    PointSensitivityBuilder protectionLegSensi =
//        protectionLegSensitivity(cds, rates.getFirst(), rates.getSecond(), referenceDate, effectiveStartDate, recoveryRate)
//            .multipliedBy(signedNotional);
//
//    return protectionLegSensi;
//  }

  private PointSensitivityBuilder protectionLegSensitivity(
      ResolvedCds cds,
      CreditDiscountFactors discountFactors,
      LegalEntitySurvivalProbabilities survivalProbabilities,
      LocalDate referenceDate,
      LocalDate effectiveStartDate,
      double recoveryRate) {

    double[] integrationSchedule = DoublesScheduleGenerator.getIntegrationsPoints(
        discountFactors.relativeYearFraction(effectiveStartDate),
        discountFactors.relativeYearFraction(cds.getProtectionEndDate()),
        discountFactors.getParameterKeys(),
        survivalProbabilities.getParameterKeys());
    int n = integrationSchedule.length;
    double[] dht = new double[n - 1];
    double[] drt = new double[n - 1];
    double[] dhrt = new double[n - 1];
    double[] p = new double[n];
    double[] q = new double[n];
    // pv
    double pv = 0d;
    double ht0 = survivalProbabilities.zeroRateYearFraction(integrationSchedule[0]);
    double rt0 = discountFactors.zeroRateYearFraction(integrationSchedule[0]);
    p[0] = Math.exp(-rt0);
    q[0] = Math.exp(-ht0);
    double b0 = p[0] * q[0];
    for (int i = 1; i < n; ++i) {
      double ht1 = survivalProbabilities.zeroRateYearFraction(integrationSchedule[i]);
      double rt1 = discountFactors.zeroRateYearFraction(integrationSchedule[i]);
      p[i] = Math.exp(-rt1);
      q[i] = Math.exp(-ht1);
      double b1 = p[i] * q[i];
      dht[i - 1] = ht1 - ht0;
      drt[i - 1] = rt1 - rt0;
      dhrt[i - 1] = dht[i - 1] + drt[i - 1];
      double dPv = 0d;
      if (Math.abs(dhrt[i - 1]) < 1e-5) {
        double eps = epsilon(-dhrt[i - 1]);
        dPv = dht[i - 1] * b0 * eps;
      } else {
        dPv = (b0 - b1) * dht[i - 1] / dhrt[i - 1];
    }
      pv += dPv;
      ht0 = ht1;
      rt0 = rt1;
      b0 = b1;
    }
    // pv sensitivity
    double eps0 = computeExtendedEpsilon(-dhrt[0], p[1], q[1], p[0], q[0]);
    PointSensitivityBuilder pvSensi = discountFactors.zeroRatePointSensitivity(integrationSchedule[0])
        .multipliedBy(-dht[0] * q[0] * eps0);
    pvSensi = pvSensi.combinedWith(
        survivalProbabilities.zeroRatePointSensitivity(integrationSchedule[0]).multipliedBy(drt[0] * p[0] * eps0 + p[0]));
    for (int i = 1; i < n - 1; ++i) {
      double epsp = computeExtendedEpsilon(-dhrt[i], p[i + 1], q[i + 1], p[i], q[i]);
      double epsm = computeExtendedEpsilon(dhrt[i - 1], p[i - 1], q[i - 1], p[i], q[i]);
      PointSensitivityBuilder pSensi = discountFactors.zeroRatePointSensitivity(integrationSchedule[i])
          .multipliedBy(-dht[i] * q[i] * epsp - dht[i - 1] * q[i] * epsm);
      PointSensitivityBuilder qSensi = survivalProbabilities.zeroRatePointSensitivity(integrationSchedule[i])
          .multipliedBy(drt[i - 1] * p[i] * epsm + drt[i] * p[i] * epsp);
      pvSensi = pvSensi.combinedWith(pSensi).combinedWith(qSensi);
    }
    if (n > 2) {
      double epsLast = computeExtendedEpsilon(dhrt[n - 2], p[n - 2], q[n - 2], p[n - 1], q[n - 1]);
      pvSensi = pvSensi.combinedWith(discountFactors.zeroRatePointSensitivity(integrationSchedule[n - 1])
          .multipliedBy(-dht[n - 2] * q[n - 1] * epsLast));
      pvSensi = pvSensi.combinedWith(survivalProbabilities.zeroRatePointSensitivity(integrationSchedule[n - 1])
          .multipliedBy(drt[n - 2] * p[n - 1] * epsLast - p[n - 1]));
    }
    
    double df = discountFactors.discountFactor(referenceDate);
    PointSensitivityBuilder dfSensi =
        discountFactors.zeroRatePointSensitivity(referenceDate).multipliedBy(-pv * (1d - recoveryRate) / (df * df));
    pvSensi = pvSensi.multipliedBy((1d - recoveryRate) / df);
    return dfSensi.combinedWith(pvSensi);
  }

  private double computeEpsilon(double dhrt, double pn, double qn, double pd, double qd) {
    if (Math.abs(dhrt) < 1e-5) {
      return epsilon(dhrt);
    }
    return (pn * qn / (pd * qd) - 1d) / dhrt;
  }

  private double computeEpsilonDerivative(double dhrt, double pn, double qn, double pd, double qd) {
    if (Math.abs(dhrt) < 1e-5) {
      return epsilonP(dhrt);
    }
    return ((pn * qn / (pd * qd) - 1d) * (dhrt - 1d) + dhrt) / (dhrt * dhrt);
  }

  private double computeExtendedEpsilon(double dhrt, double pn, double qn, double pd, double qd) {
    if (Math.abs(dhrt) < 1e-5) {
      return -0.5 - dhrt / 6d - dhrt * dhrt / 24d;
    }
    return (1d - (pn * qn / (pd * qd) - 1d) / dhrt) / dhrt;
  }

  private double computeExtendedEpsilonDerivative(double dhrt, double pn, double qn, double pd, double qd) {
    if (Math.abs(dhrt) < 1e-5) {
      return -1d / 6d - dhrt / 12d - dhrt / 40d;
    }
    return (pn * qn / (pd * qd) * (2d - dhrt) - 2d - dhrt) / Math.pow(dhrt, 3);
  }

  private PointSensitivityBuilder riskyAnnuitySensitivity(
      ResolvedCds cds,
      CreditDiscountFactors discountFactors,
      LegalEntitySurvivalProbabilities survivalProbabilities,
      LocalDate referenceDate,
      LocalDate stepinDate,
      LocalDate effectiveStartDate) {

    double pv = 0d;
    PointSensitivityBuilder pvSensi = PointSensitivityBuilder.none();
    for (CreditCouponPaymentPeriod coupon : cds.getPeriodicPayments()) {
      if (stepinDate.isBefore(coupon.getEndDate())) {
        double q = survivalProbabilities.survivalProbability(coupon.getEffectiveEndDate());
        PointSensitivityBuilder qSensi = survivalProbabilities.zeroRatePointSensitivity(coupon.getEffectiveEndDate());
        double p = discountFactors.discountFactor(coupon.getPaymentDate());
        PointSensitivityBuilder pSensi = discountFactors.zeroRatePointSensitivity(coupon.getPaymentDate());
        pv += coupon.getYearFraction() * p * q;
        pvSensi = pvSensi.combinedWith(pSensi.multipliedBy(coupon.getYearFraction() * q)
            .combinedWith(qSensi.multipliedBy(coupon.getYearFraction() * p)));
      }
    }

    if (cds.getPaymentOnDefault().isAccruedInterest()) {
      //This is needed so that the code is consistent with ISDA C when the Markit `fix' is used. For forward starting CDS (accStart > trade-date),
      //and more than one coupon, the C code generates an extra integration point (a node at protection start and one the day before) - normally
      //the second point could be ignored (since is doesn't correspond to a node of the curves, nor is it the start point), but the Markit fix is 
      //mathematically incorrect, so this point affects the result.  
      LocalDate start = cds.getPeriodicPayments().size() == 1 ? effectiveStartDate : cds.getAccrualStartDate();
      double[] integrationSchedule = DoublesScheduleGenerator.getIntegrationsPoints(
          discountFactors.relativeYearFraction(start),
          discountFactors.relativeYearFraction(cds.getProtectionEndDate()),
          discountFactors.getParameterKeys(),
          survivalProbabilities.getParameterKeys());
      for (CreditCouponPaymentPeriod coupon : cds.getPeriodicPayments()) {
        Pair<Double, PointSensitivityBuilder> pvAndSensi = singlePeriodAccrualOnDefaultSensitivity(
            coupon, effectiveStartDate, integrationSchedule, discountFactors, survivalProbabilities);
        pv += pvAndSensi.getFirst();
        pvSensi = pvSensi.combinedWith(pvAndSensi.getSecond());
      }
    }

    double df = discountFactors.discountFactor(referenceDate);
    PointSensitivityBuilder dfSensi =
        discountFactors.zeroRatePointSensitivity(referenceDate).multipliedBy(-pv / (df * df));
    pvSensi = pvSensi.multipliedBy(1d / df);

    return dfSensi.combinedWith(pvSensi);
  }

  private  Pair<Double, PointSensitivityBuilder> singlePeriodAccrualOnDefaultSensitivity(
      CreditCouponPaymentPeriod coupon,
      LocalDate effectiveStartDate,
      double[] integrationSchedule,
      CreditDiscountFactors discountFactors,
      LegalEntitySurvivalProbabilities survivalProbabilities) {

    LocalDate start =
        coupon.getEffectiveStartDate().isBefore(effectiveStartDate) ? effectiveStartDate : coupon.getEffectiveStartDate();
    if (!start.isBefore(coupon.getEffectiveEndDate())) {
      return Pair.of(0d, PointSensitivityBuilder.none()) ; //this coupon has already expired 
    }

    double[] knots = DoublesScheduleGenerator.truncateSetInclusive(discountFactors.relativeYearFraction(start),
        discountFactors.relativeYearFraction(coupon.getEffectiveEndDate()), integrationSchedule);

    final int nItems = knots.length;
    double[] dt = new double[nItems-1];
    double[] tMod = new double [nItems];
    double[] dht = new double[nItems - 1];
    double[] dhrt = new double[nItems - 1];
    double[] p = new double[nItems];
    double[] q = new double[nItems];

    double t = knots[0];
    double ht0 = survivalProbabilities.zeroRateYearFraction(t);
    PointSensitivityBuilder ht0Sensi = survivalProbabilities.zeroRateYearFractionPointSensitivity(t);
    double rt0 = discountFactors.zeroRateYearFraction(t);
    PointSensitivityBuilder rt0Sensi = discountFactors.zeroRateYearFractionPointSensitivity(t);
    p[0] = Math.exp(-rt0);
    q[0] = Math.exp(-ht0);
    double b0 = p[0] * q[0];
    PointSensitivityBuilder b0Sensi = ht0Sensi.cloned().combinedWith(rt0Sensi.cloned()).multipliedBy(-b0);

    double effStart = discountFactors.relativeYearFraction(coupon.getEffectiveStartDate());
    tMod[0] = t - effStart + omega;
    double pv = 0d;
    PointSensitivityBuilder pvSensi = PointSensitivityBuilder.none();
    for (int j = 1; j < nItems; ++j) {
      t = knots[j];
      double ht1 = survivalProbabilities.zeroRateYearFraction(t);
      PointSensitivityBuilder ht1Sensi = survivalProbabilities.zeroRateYearFractionPointSensitivity(t);
      double rt1 = discountFactors.zeroRateYearFraction(t);
      PointSensitivityBuilder rt1Sensi = discountFactors.zeroRateYearFractionPointSensitivity(t);
      p[j] = Math.exp(-rt1);
      q[j] = Math.exp(-ht1);
      double b1 = p[j] * q[j];
      PointSensitivityBuilder b1Sensi = ht1Sensi.cloned().combinedWith(rt1Sensi.cloned()).multipliedBy(-b1);

      // TODO clean sensitivity computation
      
      dt[j - 1] = knots[j] - knots[j - 1];

      dht[j - 1] = ht1 - ht0;
      double drt = rt1 - rt0;
      dhrt[j - 1] = dht[j - 1] + drt;
      PointSensitivityBuilder dhtSensi = ht1Sensi.cloned().combinedWith(ht0Sensi.multipliedBy(-1d));
      PointSensitivityBuilder dhrtSensi =
          dhtSensi.cloned().combinedWith(rt1Sensi.cloned().combinedWith(rt0Sensi.multipliedBy(-1d)));

      double tPv;
      PointSensitivityBuilder tPvSensi;
      if (formula == AccrualOnDefaultFormulae.MARKIT_FIX) {
        if (Math.abs(dhrt[j - 1]) < 1e-5) {
          double eps = epsilonP(-dhrt[j - 1]);
          tPv = dht[j - 1] * dt[j - 1] * b0 * eps;
          tPvSensi = dhtSensi.multipliedBy(dt[j - 1] * b0 * eps)
              .combinedWith(b0Sensi.multipliedBy(dht[j - 1] * eps))
              .combinedWith(dhrtSensi.multipliedBy(-dht[j - 1] * dt[j - 1] * b0 * epsilonPP(-dhrt[j - 1])));
        } else {
          tPv = dht[j - 1] * dt[j - 1] / dhrt[j - 1] * ((b0 - b1) / dhrt[j - 1] - b1);
          tPvSensi = dhtSensi.multipliedBy(dt[j - 1] / dhrt[j - 1] * ((b0 - b1) / dhrt[j - 1] - b1))
              .combinedWith(dhrtSensi
                  .multipliedBy(dht[j - 1] * dt[j - 1] / (dhrt[j - 1] * dhrt[j - 1]) * (b1 - 2d * (b0 - b1) / dhrt[j - 1])))
              .combinedWith(b0Sensi.multipliedBy(dht[j - 1] * dt[j - 1] / (dhrt[j - 1] * dhrt[j - 1])))
              .combinedWith(b1Sensi.cloned().multipliedBy(-dht[j - 1] * dt[j - 1] / dhrt[j - 1] * (1d + 1d / dhrt[j - 1])));
        }
      } else {
        tMod[j] = t - effStart + omega;
        if (Math.abs(dhrt[j - 1]) < 1e-5) {
          double eps = epsilon(-dhrt[j - 1]);
          double epsp = epsilonP(-dhrt[j - 1]);
          tPv = dht[j - 1] * b0 * (tMod[j - 1] * eps + dt[j - 1] * epsp);
          tPvSensi = dhtSensi.multipliedBy(b0 * (tMod[j-1] * eps + dt[j - 1] * epsp))
              .combinedWith(b0Sensi.multipliedBy(dht[j - 1] * (tMod[j - 1] * eps + dt[j - 1] * epsp)))
              .combinedWith(
                  dhrtSensi.multipliedBy(-dht[j - 1] * b0 * (tMod[j - 1] * epsp + dt[j - 1] * epsilonPP(-dhrt[j - 1]))));
        } else {
          tPv = dht[j - 1] / dhrt[j - 1] * (tMod[j - 1] * b0 - tMod[j] * b1 + dt[j - 1] / dhrt[j - 1] * (b0 - b1));
          tPvSensi =
              dhrtSensi.multipliedBy(
                  dht[j - 1] / (dhrt[j - 1] * dhrt[j - 1]) *
                      (-2d * dt[j - 1] / dhrt[j - 1] * (b0 - b1) - tMod[j - 1] * b0 + tMod[j] * b1))
                  .combinedWith(dhtSensi
                      .multipliedBy((tMod[j - 1] * b0 - tMod[j] * b1 + dt[j - 1] / dhrt[j - 1] * (b0 - b1)) / dhrt[j - 1]))
                  .combinedWith(b0Sensi.multipliedBy(dht[j - 1] / dhrt[j - 1] * (tMod[j - 1] + dt[j - 1] / dhrt[j - 1])))
                  .combinedWith(b1Sensi.cloned().multipliedBy(dht[j - 1] / dhrt[j - 1] * (-tMod[j] - dt[j - 1] / dhrt[j - 1])));
        }
      }

      pv += tPv;
      pvSensi = pvSensi.combinedWith(tPvSensi);
      ht0 = ht1;
      ht0Sensi = ht1Sensi;
      rt0 = rt1;
      rt0Sensi = rt1Sensi;
      b0 = b1;
      b0Sensi = b1Sensi;
    }
    double yfRatio = coupon.getYearFraction() /
        discountFactors.getDayCount().relativeYearFraction(coupon.getStartDate(), coupon.getEndDate());
     
    return Pair.of(yfRatio * pv, pvSensi.multipliedBy(yfRatio));
//    
//
//    double ht0 = survivalProbabilities.zeroRateYearFraction(t);
//    PointSensitivityBuilder ht0Sensi = survivalProbabilities.zeroRateYearFractionPointSensitivity(t);
//    double rt0 = discountFactors.zeroRateYearFraction(t);
//    PointSensitivityBuilder rt0Sensi = discountFactors.zeroRateYearFractionPointSensitivity(t);
//    p[0] = Math.exp(-rt0);
//    q[0] = Math.exp(-ht0);
//    double b0 = p[0] * q[0];
//    PointSensitivityBuilder b0Sensi = ht0Sensi.cloned().combinedWith(rt0Sensi.cloned()).multipliedBy(-b0);
//
//    double effStart = discountFactors.relativeYearFraction(coupon.getEffectiveStartDate());
//    tMod[0] = t - effStart + omega;
//    double pv = 0d;
//    PointSensitivityBuilder pvSensi = PointSensitivityBuilder.none();
//    for (int j = 1; j < nItems; ++j) {
//      t = knots[j];
//      double ht1 = survivalProbabilities.zeroRateYearFraction(t);
//      PointSensitivityBuilder ht1Sensi = survivalProbabilities.zeroRateYearFractionPointSensitivity(t);
//      double rt1 = discountFactors.zeroRateYearFraction(t);
//      PointSensitivityBuilder rt1Sensi = discountFactors.zeroRateYearFractionPointSensitivity(t);
//      p[j] = Math.exp(-rt1);
//      q[j] = Math.exp(-ht1);
//      double b1 = p[j] * q[j];
//      PointSensitivityBuilder b1Sensi = ht1Sensi.cloned().combinedWith(rt1Sensi.cloned()).multipliedBy(-b1);
//
//      // TODO clean sensitivity computation
//      
//      dt[j - 1] = knots[j] - knots[j - 1];
//
//      dht[j - 1] = ht1 - ht0;
//      double drt = rt1 - rt0;
//      dhrt[j - 1] = dht[j - 1] + drt;
//      PointSensitivityBuilder dhtSensi = ht1Sensi.cloned().combinedWith(ht0Sensi.multipliedBy(-1d));
//      PointSensitivityBuilder dhrtSensi =
//          dhtSensi.cloned().combinedWith(rt1Sensi.cloned().combinedWith(rt0Sensi.multipliedBy(-1d)));
//
//      double tPv;
//      PointSensitivityBuilder tPvSensi;
//      if (formula == AccrualOnDefaultFormulae.MARKIT_FIX) {
//        if (Math.abs(dhrt[j - 1]) < 1e-5) {
//          double eps = epsilonP(-dhrt[j - 1]);
//          tPv = dht[j - 1] * dt[j - 1] * b0 * eps;
//          tPvSensi = dhtSensi.multipliedBy(dt[j - 1] * b0 * eps)
//              .combinedWith(b0Sensi.multipliedBy(dht[j - 1] * eps))
//              .combinedWith(dhrtSensi.multipliedBy(-dht[j - 1] * dt[j - 1] * b0 * epsilonPP(-dhrt[j - 1])));
//        } else {
//          tPv = dht[j - 1] * dt[j - 1] / dhrt[j - 1] * ((b0 - b1) / dhrt[j - 1] - b1);
//          tPvSensi = dhtSensi.multipliedBy(dt[j - 1] / dhrt[j - 1] * ((b0 - b1) / dhrt[j - 1] - b1))
//              .combinedWith(dhrtSensi
//                  .multipliedBy(dht[j - 1] * dt[j - 1] / (dhrt[j - 1] * dhrt[j - 1]) * (b1 - 2d * (b0 - b1) / dhrt[j - 1])))
//              .combinedWith(b0Sensi.multipliedBy(dht[j - 1] * dt[j - 1] / (dhrt[j - 1] * dhrt[j - 1])))
//              .combinedWith(b1Sensi.cloned().multipliedBy(-dht[j - 1] * dt[j - 1] / dhrt[j - 1] * (1d + 1d / dhrt[j - 1])));
//        }
//      } else {
//        tMod[j] = t - effStart + omega;
//        if (Math.abs(dhrt[j - 1]) < 1e-5) {
//          double eps = epsilon(-dhrt[j - 1]);
//          double epsp = epsilonP(-dhrt[j - 1]);
//          tPv = dht[j - 1] * b0 * (tMod[j - 1] * eps + dt[j - 1] * epsp);
//          tPvSensi = dhtSensi.multipliedBy(b0 * (tMod[j-1] * eps + dt[j - 1] * epsp))
//              .combinedWith(b0Sensi.multipliedBy(dht[j - 1] * (tMod[j - 1] * eps + dt[j - 1] * epsp)))
//              .combinedWith(
//                  dhrtSensi.multipliedBy(-dht[j - 1] * b0 * (tMod[j - 1] * epsp + dt[j - 1] * epsilonPP(-dhrt[j - 1]))));
//        } else {
//          tPv = dht[j - 1] / dhrt[j - 1] * (tMod[j - 1] * b0 - tMod[j] * b1 + dt[j - 1] / dhrt[j - 1] * (b0 - b1));
//          tPvSensi =
//              dhrtSensi.multipliedBy(
//                  dht[j - 1] / (dhrt[j - 1] * dhrt[j - 1]) *
//                      (-2d * dt[j - 1] / dhrt[j - 1] * (b0 - b1) - tMod[j - 1] * b0 + tMod[j] * b1))
//                  .combinedWith(dhtSensi
//                      .multipliedBy((tMod[j - 1] * b0 - tMod[j] * b1 + dt[j - 1] / dhrt[j - 1] * (b0 - b1)) / dhrt[j - 1]))
//                  .combinedWith(b0Sensi.multipliedBy(dht[j - 1] / dhrt[j - 1] * (tMod[j - 1] + dt[j - 1] / dhrt[j - 1])))
//                  .combinedWith(b1Sensi.cloned().multipliedBy(dht[j - 1] / dhrt[j - 1] * (-tMod[j] - dt[j - 1] / dhrt[j - 1])));
//        }
//      }
//
//      pv += tPv;
//      pvSensi = pvSensi.combinedWith(tPvSensi);
//      ht0 = ht1;
//      ht0Sensi = ht1Sensi;
//      rt0 = rt1;
//      rt0Sensi = rt1Sensi;
//      b0 = b1;
//      b0Sensi = b1Sensi;
//    }
//    double yfRatio = coupon.getYearFraction() /
//        discountFactors.getDayCount().relativeYearFraction(coupon.getStartDate(), coupon.getEndDate());
//     
//    return Pair.of(yfRatio * pv, pvSensi.multipliedBy(yfRatio));
  }

  //-------------------------------------------------------------------------
  public double protectionLeg(
      ResolvedCds cds,
      CreditRatesProvider ratesProvider,
      LocalDate referenceDate,
      ReferenceData refData) {

    ArgChecker.notNull(cds, "cds");
    ArgChecker.notNull(ratesProvider, "ratesProvider");
    ArgChecker.notNull(refData, "refData");
    if (!cds.getProtectionEndDate().isAfter(ratesProvider.getValuationDate())) { //short cut already expired CDSs
      return 0d;
    }
    LocalDate stepinDate = cds.getStepinDateOffset().adjust(ratesProvider.getValuationDate(), refData);
    LocalDate effectiveStartDate = cds.getEffectiveStartDate(stepinDate);
    double recoveryRate = recoveryRate(cds, ratesProvider);
    Pair<CreditDiscountFactors, LegalEntitySurvivalProbabilities> rates = reduceDiscountFactors(cds, ratesProvider);
    return protectionLeg(cds, rates.getFirst(), rates.getSecond(), referenceDate, effectiveStartDate, recoveryRate);
  }

  public double riskyAnnuity(
      ResolvedCds cds,
      CreditRatesProvider ratesProvider,
      LocalDate referenceDate,
      PriceType priceType,
      ReferenceData refData) {

    ArgChecker.notNull(cds, "cds");
    ArgChecker.notNull(ratesProvider, "ratesProvider");
    ArgChecker.notNull(refData, "refData");
    if (!cds.getProtectionEndDate().isAfter(ratesProvider.getValuationDate())) { //short cut already expired CDSs
      return 0d;
    }
    LocalDate stepinDate = cds.getStepinDateOffset().adjust(ratesProvider.getValuationDate(), refData);
    LocalDate effectiveStartDate = cds.getEffectiveStartDate(stepinDate);
    Pair<CreditDiscountFactors, LegalEntitySurvivalProbabilities> rates = reduceDiscountFactors(cds, ratesProvider);
    return riskyAnnuity(cds, rates.getFirst(), rates.getSecond(), referenceDate, stepinDate, effectiveStartDate, priceType);
  }

  //-------------------------------------------------------------------------
  private double protectionLeg(ResolvedCds cds,
      CreditDiscountFactors discountFactors,
      LegalEntitySurvivalProbabilities survivalProbabilities,
      LocalDate referenceDate,
      LocalDate effectiveStartDate,
      double recoveryRate) {

    double protectionFull = protectionFull(cds, discountFactors, survivalProbabilities, referenceDate, effectiveStartDate);
    return (1d - recoveryRate) * protectionFull;
  }

  private double protectionFull(
      ResolvedCds cds,
      CreditDiscountFactors discountFactors,
      LegalEntitySurvivalProbabilities survivalProbabilities,
      LocalDate referenceDate,
      LocalDate effectiveStartDate) {

    double[] integrationSchedule = DoublesScheduleGenerator.getIntegrationsPoints(
        discountFactors.relativeYearFraction(effectiveStartDate),
        discountFactors.relativeYearFraction(cds.getProtectionEndDate()),
        discountFactors.getParameterKeys(),
        survivalProbabilities.getParameterKeys());

    double pv = 0d;
    double ht0 = survivalProbabilities.zeroRateYearFraction(integrationSchedule[0]);
    double rt0 = discountFactors.zeroRateYearFraction(integrationSchedule[0]);
    double b0 = Math.exp(-ht0 - rt0);
    int n = integrationSchedule.length;
    for (int i = 1; i < n; ++i) {
      double ht1 = survivalProbabilities.zeroRateYearFraction(integrationSchedule[i]);
      double rt1 = discountFactors.zeroRateYearFraction(integrationSchedule[i]);
      double b1 = Math.exp(-ht1 - rt1);
      double dht = ht1 - ht0;
      double drt = rt1 - rt0;
      double dhrt = dht + drt;

      // The formula has been modified from ISDA (but is equivalent) to avoid log(exp(x)) and explicitly
      // calculating the time step - it also handles the limit
      double dPV = 0d;
      if (Math.abs(dhrt) < 1e-5) {
        dPV = dht * b0 * epsilon(-dhrt);
      } else {
        dPV = (b0 - b1) * dht / dhrt;
      }

      pv += dPV;
      ht0 = ht1;
      rt0 = rt1;
      b0 = b1;
    }

    // roll to the cash settle date
    double df = discountFactors.discountFactor(referenceDate);
    pv /= df;

    return pv;
  }

  private double riskyAnnuity(
      ResolvedCds cds,
      CreditDiscountFactors discountFactors,
      LegalEntitySurvivalProbabilities survivalProbabilities,
      LocalDate referenceDate,
      LocalDate stepinDate,
      LocalDate effectiveStartDate,
      PriceType priceType) {

    double pv = 0d;
    for (CreditCouponPaymentPeriod coupon : cds.getPeriodicPayments()) {
      if (stepinDate.isBefore(coupon.getEndDate())) {
        double q = survivalProbabilities.survivalProbability(coupon.getEffectiveEndDate());
        double p = discountFactors.discountFactor(coupon.getPaymentDate());
        pv += coupon.getYearFraction() * p * q;
      }
    }

    if (cds.getPaymentOnDefault().isAccruedInterest()) {
      //This is needed so that the code is consistent with ISDA C when the Markit `fix' is used. For forward starting CDS (accStart > trade-date),
      //and more than one coupon, the C code generates an extra integration point (a node at protection start and one the day before) - normally
      //the second point could be ignored (since is doesn't correspond to a node of the curves, nor is it the start point), but the Markit fix is 
      //mathematically incorrect, so this point affects the result.  
      LocalDate start = cds.getPeriodicPayments().size() == 1 ? effectiveStartDate : cds.getAccrualStartDate();
      double[] integrationSchedule = DoublesScheduleGenerator.getIntegrationsPoints(
          discountFactors.relativeYearFraction(start),
          discountFactors.relativeYearFraction(cds.getProtectionEndDate()),
          discountFactors.getParameterKeys(),
          survivalProbabilities.getParameterKeys());
      for (CreditCouponPaymentPeriod coupon : cds.getPeriodicPayments()) {
        pv += singlePeriodAccrualOnDefault(
            coupon, effectiveStartDate, integrationSchedule, discountFactors, survivalProbabilities);
      }
    }
    // roll to the cash settle date
    double df = discountFactors.discountFactor(referenceDate);
    pv /= df;

    if (priceType.isCleanPrice()) {
      pv -= cds.accruedYearFraction(stepinDate);
    }

    return pv;
  }

  private double singlePeriodAccrualOnDefault(
      CreditCouponPaymentPeriod coupon,
      LocalDate effectiveStartDate,
      double[] integrationSchedule,
      CreditDiscountFactors discountFactors,
      LegalEntitySurvivalProbabilities survivalProbabilities) {

    LocalDate start =
        coupon.getEffectiveStartDate().isBefore(effectiveStartDate) ? effectiveStartDate : coupon.getEffectiveStartDate();
    if (!start.isBefore(coupon.getEffectiveEndDate())) {
      return 0d; //this coupon has already expired 
    }

    double[] knots = DoublesScheduleGenerator.truncateSetInclusive(discountFactors.relativeYearFraction(start),
        discountFactors.relativeYearFraction(coupon.getEffectiveEndDate()), integrationSchedule);

    double t = knots[0];
    double ht0 = survivalProbabilities.zeroRateYearFraction(t);
    double rt0 = discountFactors.zeroRateYearFraction(t);
    double b0 = Math.exp(-rt0 - ht0);

    double effStart = discountFactors.relativeYearFraction(coupon.getEffectiveStartDate());
    double t0 = t - effStart + omega;
    double pv = 0d;
    final int nItems = knots.length;
    for (int j = 1; j < nItems; ++j) {
      t = knots[j];
      double ht1 = survivalProbabilities.zeroRateYearFraction(t);
      double rt1 = discountFactors.zeroRateYearFraction(t);
      double b1 = Math.exp(-rt1 - ht1);

      double dt = knots[j] - knots[j - 1];

      double dht = ht1 - ht0;
      double drt = rt1 - rt0;
      double dhrt = dht + drt;

      double tPV;
      if (formula == AccrualOnDefaultFormulae.MARKIT_FIX) {
        if (Math.abs(dhrt) < 1e-5) {
          tPV = dht * dt * b0 * Epsilon.epsilonP(-dhrt);
        } else {
          tPV = dht * dt / dhrt * ((b0 - b1) / dhrt - b1);
        }
      } else {
        double t1 = t - effStart + omega;
        if (Math.abs(dhrt) < 1e-5) {
          tPV = dht * b0 * (t0 * epsilon(-dhrt) + dt * Epsilon.epsilonP(-dhrt));
        } else {
          tPV = dht / dhrt * (t0 * b0 - t1 * b1 + dt / dhrt * (b0 - b1));
        }
        t0 = t1;
      }

      pv += tPV;
      ht0 = ht1;
      rt0 = rt1;
      b0 = b1;
    }

    double yearFractionCurve =
        discountFactors.getDayCount().relativeYearFraction(coupon.getStartDate(), coupon.getEndDate());
    return coupon.getYearFraction() * pv / yearFractionCurve;
  }

  //-------------------------------------------------------------------------
  private double recoveryRate(ResolvedCds cds, CreditRatesProvider ratesProvider) {
    RecoveryRates recoveryRates = ratesProvider.recoveryRates(cds.getLegalEntityId());
    ArgChecker.isTrue(recoveryRates instanceof ConstantRecoveryRates, "recoveryRates must be ConstantRecoveryRates");
    return recoveryRates.recoveryRate(cds.getProtectionEndDate());
  }

  private Pair<CreditDiscountFactors, LegalEntitySurvivalProbabilities> reduceDiscountFactors(
      ResolvedCds cds, CreditRatesProvider ratesProvider) {
    Currency currency = cds.getCurrency();
    CreditDiscountFactors discountFactors = ratesProvider.discountFactors(currency);
    ArgChecker.isTrue(discountFactors instanceof IsdaCompliantZeroRateDiscountFactors,
        "discount factors must be IsdaCompliantZeroRateDiscountFactors");
    LegalEntitySurvivalProbabilities survivalProbabilities =
        ratesProvider.survivalProbabilities(cds.getLegalEntityId(), currency);
    ArgChecker.isTrue(survivalProbabilities.getSurvivalProbabilities() instanceof IsdaCompliantZeroRateDiscountFactors,
        "survival probabilities must be IsdaCompliantZeroRateDiscountFactors");
    ArgChecker.isTrue(discountFactors.getDayCount().equals(survivalProbabilities.getSurvivalProbabilities().getDayCount()),
        "day count conventions of discounting curve and credit curve must be the same");
    return Pair.of(discountFactors, survivalProbabilities);
  }

}