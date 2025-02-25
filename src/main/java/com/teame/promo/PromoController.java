package com.teame.promo;

import com.teame.club.Club;
import com.teame.club.ClubDTO;
import com.teame.club.comment.QnA;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@RestController
public class PromoController {

    private final PromoService promoService;
    private final PromoRepository promoRepository;
    private final PromoDataParser promoDataParser;

    @Autowired
    public PromoController(PromoService promoService, PromoRepository promoRepository, PromoDataParser promoDataParser){
        this.promoService = promoService;
        this.promoRepository = promoRepository;
        this.promoDataParser = promoDataParser;
    }

    @GetMapping("/api/daily-up/fetchAll/{page}/{size}")
    public ResponseEntity<?> fetchAllPromos(@PathVariable int page,
                                            @PathVariable int size,
                                            PagedResourcesAssembler<Promo> assembler) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("postedAt").descending());
            Page<Promo> promoPage = promoRepository.findAll(pageable);
            PagedModel<EntityModel<Promo>> pagedModel = assembler.toModel(promoPage);
            return ResponseEntity.status(HttpStatus.OK).body(pagedModel);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Promo 불러오기 실패: " + e.getMessage());
        }
    }
    
    @GetMapping("/api/daily-up/fetchById/{promoId}")
    public ResponseEntity<?> fetchPromoByIdAPI(@PathVariable Long promoId) {
        return promoService.fetchPromoById(promoId);
    }

    // Manual로 데이터 파싱 수행
    @GetMapping("/api/admin/parse")
    public ResponseEntity<?> fetchParseData()
    {
        try {
            int cnt = promoDataParser.runSeleniumTask();
            return ResponseEntity.ok("Data parsing completed successfully. Parsed articles: "+cnt);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
