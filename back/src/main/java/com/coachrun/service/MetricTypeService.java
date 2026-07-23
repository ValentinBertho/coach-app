package com.coachrun.service;

import com.coachrun.dto.request.MetricTypeRequest;
import com.coachrun.dto.response.MetricTypeResponse;
import com.coachrun.entity.MetricType;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.MetricTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Catalogue de métriques : lecture (global + custom du club) et création de métriques custom.
 * Le catalogue builtin global reste en lecture seule (seedé en migration 044).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricTypeService {

    private final MetricTypeRepository metricTypeRepository;
    private final ClubRepository clubRepository;

    public List<MetricTypeResponse> list(UUID clubId) {
        return metricTypeRepository.findVisibleForClub(clubId).stream()
                .map(MetricTypeResponse::from)
                .toList();
    }

    @Transactional
    public MetricTypeResponse create(UUID clubId, MetricTypeRequest req) {
        MetricType m = new MetricType();
        m.setClub(clubRepository.getReferenceById(clubId));
        m.setCode(req.code().trim());
        m.setName(req.name().trim());
        m.setUnit(req.unit());
        m.setFormat(req.format());
        m.setDirection(req.direction());
        m.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 100);
        m.setBuiltin(false);
        return MetricTypeResponse.from(metricTypeRepository.save(m));
    }
}
