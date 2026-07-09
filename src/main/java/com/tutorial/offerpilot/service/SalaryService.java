/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.salary.NegotiationScriptRequest;
import com.tutorial.offerpilot.dto.salary.OfferCompareRequest;
import com.tutorial.offerpilot.dto.tool.NegotiationScriptResult;
import com.tutorial.offerpilot.dto.tool.OfferComparisonResult;
import com.tutorial.offerpilot.dto.tool.SalarySearchResult;
import com.tutorial.offerpilot.entity.SalaryRecord;
import com.tutorial.offerpilot.repository.SalaryRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryService {

    private final SalaryRecordRepository salaryRepo;

    /**
     * 搜索薪资数据，按公司名/职位模糊查询。
     */
    public SalarySearchResult searchSalary(String company, String position) {
        String companyFilter = company != null ? company.toLowerCase() : "";
        String positionFilter = (position != null && !position.isBlank()) ? position.toLowerCase() : null;

        List<SalaryRecord> records = salaryRepo.findAll().stream()
                .filter(r -> r.getCompanyName() != null
                        && r.getCompanyName().toLowerCase().contains(companyFilter))
                .filter(r -> positionFilter == null
                        || (r.getPosition() != null && r.getPosition().toLowerCase().contains(positionFilter)))
                .toList();

        List<SalarySearchResult.SalaryItem> items = records.stream()
                .map(r -> {
                    SalarySearchResult.SalaryItem item = new SalarySearchResult.SalaryItem();
                    item.setCompany(r.getCompanyName());
                    item.setPosition(r.getPosition());
                    item.setBaseRange(formatSalaryRange(r.getBaseSalary()));
                    item.setBonusRange(r.getBonusInfo());
                    item.setStockInfo(r.getStockInfo());
                    item.setSource(r.getSource());
                    item.setRelevanceScore(1.0f);
                    return item;
                })
                .toList();

        log.info("Salary search: company={}, position={}, results={}", company, position, items.size());
        return new SalarySearchResult(items.size(), items);
    }

    /**
     * 对比多个 offer。
     */
    public OfferComparisonResult compareOffers(OfferCompareRequest request) {
        List<OfferCompareRequest.OfferItem> offers = request.getOffers();
        if (offers == null || offers.size() < 2) {
            return new OfferComparisonResult("offer数量不足", List.of(), "请提供至少两个offer进行对比");
        }

        List<OfferComparisonResult.OfferAnalysis> analyses = new ArrayList<>();
        OfferCompareRequest.OfferItem bestOffer = null;
        double bestPackage = 0;

        for (OfferCompareRequest.OfferItem offer : offers) {
            double base = offer.getBase() != null ? offer.getBase() : 0;
            int months = offer.getMonths() != null ? offer.getMonths() : 12;
            double totalPackage = base * months;

            OfferComparisonResult.OfferAnalysis analysis = new OfferComparisonResult.OfferAnalysis();
            analysis.setCompany(offer.getCompany());
            analysis.setTotalPackage(Math.round(totalPackage * 100.0) / 100.0);
            analysis.setPros(buildOfferPros(offer, totalPackage));
            analysis.setCons(buildOfferCons(offer));
            analyses.add(analysis);

            if (totalPackage > bestPackage) {
                bestPackage = totalPackage;
                bestOffer = offer;
            }
        }

        String summary = buildComparisonSummary(analyses);
        String recommendation = bestOffer != null
                ? "综合薪资来看，推荐 " + bestOffer.getCompany() + " 的 offer，年包约 "
                    + String.format("%.2f", bestPackage) + " 万"
                : "无法确定最佳 offer";

        log.info("Offer comparison: {} offers analyzed", offers.size());
        return new OfferComparisonResult(summary, analyses, recommendation);
    }

    /**
     * 生成薪资谈判话术。
     */
    public NegotiationScriptResult generateNegotiationScript(NegotiationScriptRequest request) {
        String style = request.getNegotiationStyle();
        double target = request.getTargetSalary() != null ? request.getTargetSalary() : 0;

        String openingLine = buildOpeningLine(style, request.getCurrentOffer(), target);
        List<String> talkingPoints = buildTalkingPoints(style, target);
        List<String> counterArguments = buildCounterArguments(style);
        String closingLine = buildClosingLine(style);

        log.info("Negotiation script generated: style={}, targetSalary={}", style, target);
        return new NegotiationScriptResult(openingLine, talkingPoints, counterArguments, closingLine);
    }

    private String formatSalaryRange(BigDecimal base) {
        if (base == null) {
            return "暂无数据";
        }
        double low = base.doubleValue() * 0.8;
        double high = base.doubleValue() * 1.2;
        return String.format("%.0fK - %.0fK", low, high);
    }

    private List<String> buildOfferPros(OfferCompareRequest.OfferItem offer, double totalPackage) {
        List<String> pros = new ArrayList<>();
        if (totalPackage > 0) {
            pros.add("年包总额清晰");
        }
        if (offer.getBonus() != null && !offer.getBonus().isBlank()) {
            pros.add("有奖金激励");
        }
        if (offer.getStock() != null && !offer.getStock().isBlank()) {
            pros.add("有股权/期权激励");
        }
        if (offer.getLocation() != null && !offer.getLocation().isBlank()) {
            pros.add("工作地点明确：" + offer.getLocation());
        }
        return pros;
    }

    private List<String> buildOfferCons(OfferCompareRequest.OfferItem offer) {
        List<String> cons = new ArrayList<>();
        if (offer.getBase() == null || offer.getBase() <= 0) {
            cons.add("基础薪资信息不明确");
        }
        if (offer.getStock() == null || offer.getStock().isBlank()) {
            cons.add("缺乏股权激励信息");
        }
        return cons;
    }

    private String buildComparisonSummary(List<OfferComparisonResult.OfferAnalysis> analyses) {
        StringBuilder sb = new StringBuilder();
        analyses.sort(Comparator.comparing(OfferComparisonResult.OfferAnalysis::getTotalPackage).reversed());
        for (int i = 0; i < analyses.size(); i++) {
            OfferComparisonResult.OfferAnalysis a = analyses.get(i);
            sb.append(String.format("%d. %s：年包约 %.2f 万", i + 1, a.getCompany(), a.getTotalPackage()));
            if (i < analyses.size() - 1) {
                sb.append("；");
            }
        }
        return sb.toString();
    }

    private String buildOpeningLine(String style, String currentOffer, double targetSalary) {
        return switch (style) {
            case "assertive" -> "非常感谢贵公司的认可，我对这个机会非常感兴趣。在仔细考虑后，我希望就薪资方案进行一些讨论。";
            case "moderate" -> "非常感谢您的 offer，我很珍惜这个机会。在做出最终决定前，我想就薪资待遇沟通一下。";
            default -> "感谢贵公司的信任和 offer。我想进一步了解薪资方案的细节，希望能达成双方都满意的结果。";
        };
    }

    private List<String> buildTalkingPoints(String style, double targetSalary) {
        List<String> points = new ArrayList<>();
        points.add("强调自身技能和经验的独特价值");
        points.add("提及行业标准和市场薪资水平");
        if (targetSalary > 0) {
            points.add("明确表达期望薪资范围：" + String.format("%.0f", targetSalary) + " 万/年");
        }
        points.add("表达对公司和团队的认同与热情");
        return points;
    }

    private List<String> buildCounterArguments(String style) {
        List<String> args = new ArrayList<>();
        args.add("如果薪资暂时无法调整，是否可以考虑增加签字费或搬迁补贴");
        args.add("了解是否有年度调薪机制和晋升通道");
        args.add("询问期权/股权授予的具体方案和时间表");
        return args;
    }

    private String buildClosingLine(String style) {
        return switch (style) {
            case "assertive" -> "我相信我的能力可以为团队带来显著价值，期待我们能在薪资方案上达成共识。";
            case "moderate" -> "我理解公司有相应的薪资体系，希望能找到一个双方都满意的平衡点。";
            default -> "无论结果如何，都非常感谢这次沟通的机会，期待进一步交流。";
        };
    }
}
