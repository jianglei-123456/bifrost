/**
 * 图书领域业务（M2-book）：Book 实体、BookParser/Registry、BookScanService、BookCoverService。
 *
 * <p>与 {@code com.bifrost.core.audio} 物理隔开（ADR-0004）：不复用 music 聚合/服务，
 * 扫描器独立 ReentrantLock，状态机/事件/控制器全独立。</p>
 */
package com.bifrost.core.book;