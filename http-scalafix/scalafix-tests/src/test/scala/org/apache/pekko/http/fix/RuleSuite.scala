/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.fix

import org.scalatest.FunSpecLike
import scalafix.testkit.AbstractSemanticRuleSuite

class RuleSuite extends AbstractSemanticRuleSuite with FunSpecLike {
  runAllTests()
}
