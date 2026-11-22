package com.todokanai.composepracticenew.testviews

import com.todokanai.composepracticenew.model.ByteAmount
import com.todokanai.composepracticenew.model.ProgressPhase
import org.junit.Assert.fail

/** 이 단계가 바이트 진행(Bytes)이어야 한다고 단정하고 그 값을 돌려준다. 다른 단계면 테스트가 실패한다. */
fun ProgressPhase.asBytes(): ProgressPhase.Bytes = this as? ProgressPhase.Bytes ?: fail("Bytes여야 하지만 $this").let { error("unreachable") }

/** 이 단계가 항목 수 진행(Counted)이어야 한다고 단정하고 그 값을 돌려준다. 다른 단계면 테스트가 실패한다. */
fun ProgressPhase.asCounted(): ProgressPhase.Counted = this as? ProgressPhase.Counted ?: fail("Counted여야 하지만 $this").let { error("unreachable") }

/** 이 단계가 전체 크기를 아는 바이트 진행(Bytes + Known)이어야 한다고 단정하고 그 양을 돌려준다. */
fun ProgressPhase.asKnown(): ByteAmount.Known = asBytes().amount as? ByteAmount.Known ?: fail("Known이어야 하지만 $this").let { error("unreachable") }

/** 이 단계가 크기를 모르는 파일이 섞인 바이트 진행(Bytes + PartlyUnknown)이어야 한다고 단정하고 그 양을 돌려준다. */
fun ProgressPhase.asPartlyUnknown(): ByteAmount.PartlyUnknown = asBytes().amount as? ByteAmount.PartlyUnknown ?: fail("PartlyUnknown이어야 하지만 $this").let { error("unreachable") }
