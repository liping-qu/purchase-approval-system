package com.enterprise.procurement.business;

import com.enterprise.procurement.persistence.mapper.SequenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号生成：{@code PR20261029000042} = 前缀 + 日期 + 当日 6 位流水。
 *
 * <p>为什么不用原来那套 {@code "PR" + yyyyMMddHHmmssSSS}：</p>
 * <ul>
 *   <li><b>时间戳会撞。</b>同一毫秒内两个请求生成的字符串完全相同，
 *       直接违反 {@code uk_request_no}，接口返回 500。压在压力下这是必现的，
 *       不是"概率极低"。</li>
 *   <li><b>UUID 不满足业务诉求。</b>单号要给人看、要能按时间排序、要能口头报给同事，
 *       32 位十六进制做不到。</li>
 *   <li><b>Redis INCR 引入新依赖。</b>本项目 Redis 是可选的旁路组件，
 *       让它成为下单链路的关键路径，等于把可用性从 MySQL 降到 min(MySQL, Redis)。</li>
 * </ul>
 *
 * <p>因此用 MySQL 自己维护号段：一条 upsert 语句原子自增，天然串行、不丢号、不重号，
 * 且完全在同一个本地事务里，不存在"号发出去了但单没落库"的分布式不一致。</p>
 *
 * <p>{@code Propagation.MANDATORY}：号段的"自增 + 回读"依赖同一个数据库连接，
 * 必须运行在调用方已开启的事务里。加这个约束后，任何忘记加 {@code @Transactional}
 * 的新调用点都会立刻在开发期报错，而不是在线上偶发取到别人的号。</p>
 */
@Service
public class DocumentNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SEQ_REQUEST = "REQUEST";
    private static final String SEQ_ORDER = "ORDER";
    private static final String SEQ_STOCK = "STOCK";

    private final SequenceMapper sequences;

    public DocumentNoGenerator(SequenceMapper sequences) {
        this.sequences = sequences;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextRequestNo() {
        return next("PR", SEQ_REQUEST);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextOrderNo() {
        return next("PO", SEQ_ORDER);
    }

    /**
     * 手工调整库存的单号（{@code ST20261030000001}）。
     *
     * <p>为什么要单号而不是直接用时间戳：库存流水上有
     * {@code (reference_no, product_id, type)} 唯一键，时间戳在同一毫秒会撞；
     * 而且仓库对账时要能按单号把一次调整的多个商品串起来。</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextStockNo() {
        return next("ST", SEQ_STOCK);
    }

    private String next(String prefix, String name) {
        sequences.bump(name);
        long value = sequences.currentValue();
        // 每日 999999 个号远超中小企业采购单据量；真到瓶颈应改为不按日期分片或换雪花号
        return prefix + LocalDate.now().format(DAY) + String.format("%06d", value);
    }
}
