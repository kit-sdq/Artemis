package de.tum.cit.aet.artemis.lecture;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ResourceUtils;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitSplitDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitSplitInformationDTO;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitProcessingService;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AttachmentVideoUnitsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "attachmentunitsintegrationtest";

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Autowired
    private SlideTestRepository slideRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureUnitProcessingService lectureUnitProcessingService;

    private LectureUnitSplitInformationDTO lectureUnitSplits;

    private Lecture lecture1;

    private Lecture invalidLecture;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        this.invalidLecture = lectureUtilService.createLecture(null, null);
        List<LectureUnitSplitDTO> units = new ArrayList<>();
        this.lectureUnitSplits = new LectureUnitSplitInformationDTO(units, 1, "Break");
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        slideRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void testAll_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAll_LectureWithoutCourse_shouldReturnBadRequest() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");

        request.postWithMultipartFile("/api/lecture/lectures/" + invalidLecture.getId() + "/attachment-video-units/upload", null, "upload", createLectureFile(true), String.class,
                HttpStatus.BAD_REQUEST);
        request.get("/api/lecture/lectures/" + invalidLecture.getId() + "/attachment-video-units/data/any-file", HttpStatus.BAD_REQUEST, LectureUnitSplitInformationDTO.class);
        request.get("/api/lecture/lectures/" + invalidLecture.getId() + "/attachment-video-units/slides-to-remove/any-file", HttpStatus.BAD_REQUEST,
                LectureUnitSplitInformationDTO.class, params);
        request.postListWithResponseBody("/api/lecture/lectures/" + invalidLecture.getId() + "/attachment-video-units/split/any-file", lectureUnitSplits, AttachmentVideoUnit.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAll_WrongLecture_shouldReturnNotFound() throws Exception {
        // Tests that files created for another lecture are not accessible
        // even by instructors of other lectures
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(invalidLecture.getId(), lectureFile);
        Path filePath = lectureUnitProcessingService.getPathForTempFilename(invalidLecture.getId(), filename);

        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/data/" + filename, HttpStatus.NOT_FOUND, LectureUnitSplitInformationDTO.class);
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/slides-to-remove/" + filename, HttpStatus.NOT_FOUND,
                LectureUnitSplitInformationDTO.class, params);
        request.postListWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/split/" + filename, lectureUnitSplits, AttachmentVideoUnit.class,
                HttpStatus.NOT_FOUND);
        assertThat(Files.exists(filePath)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAll_IOException_ShouldReturnInternalServerError() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "");

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readAllBytes(any())).thenThrow(IOException.class);
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/data/" + filename, HttpStatus.INTERNAL_SERVER_ERROR,
                    LectureUnitSplitInformationDTO.class);
            request.getList("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/slides-to-remove/" + filename, HttpStatus.INTERNAL_SERVER_ERROR, Integer.class,
                    params);
            request.postListWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/split/" + filename, lectureUnitSplits,
                    AttachmentVideoUnit.class, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void uploadSlidesForProcessing_asInstructor_shouldGetFilename() throws Exception {
        var filePart = createLectureFile(true);

        String uploadInfo = request.postWithMultipartFile("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/upload", null, "upload", filePart, String.class,
                HttpStatus.OK);
        assertThat(uploadInfo).contains(".pdf");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void uploadSlidesForProcessing_asInstructor_shouldThrowError() throws Exception {
        var filePartWord = createLectureFile(false);
        request.postWithMultipartFile("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/upload", null, "upload", filePartWord,
                LectureUnitSplitInformationDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachmentVideoUnitsData_asInstructor_shouldGetUnitsInformationVideo() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        LectureUnitSplitInformationDTO lectureUnitSplitInfo = request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/data/" + filename, HttpStatus.OK,
                LectureUnitSplitInformationDTO.class);

        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachmentVideoUnitsData_asInstructor_realSlides() throws Exception {
        var realFile = readFromFile();
        String filename = manualFileUpload(lecture1.getId(), realFile);

        LectureUnitSplitInformationDTO lectureUnitSplitInfo = request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/data/" + filename, HttpStatus.OK,
                LectureUnitSplitInformationDTO.class);

        assertThat(lectureUnitSplitInfo.units()).hasSize(4);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(102);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachmentVideoUnitsData_asInstructor_shouldThrowError() throws Exception {
        var lectureFile = createLectureFile(false);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/data/" + filename, HttpStatus.BAD_REQUEST, LectureUnitSplitInformationDTO.class);
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/data/non-existent-file", HttpStatus.NOT_FOUND, LectureUnitSplitInformationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSlidesToRemove_asInstructor_shouldGetUnitsInformation() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");

        List<Integer> removedSlides = request.getList("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/slides-to-remove/" + filename, HttpStatus.OK,
                Integer.class, params);

        assertThat(removedSlides).hasSize(2);
        // index is one lower than in createLectureFile because the loop starts at 1.
        assertThat(removedSlides).contains(5, 6);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSlidesToRemove_asInstructor_shouldThrowError() throws Exception {
        var lectureFile = createLectureFile(false);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");

        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/slides-to-remove/" + filename, HttpStatus.BAD_REQUEST,
                LectureUnitSplitInformationDTO.class, params);
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/slides-to-remove/non-existent-file", HttpStatus.NOT_FOUND,
                LectureUnitSplitInformationDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentVideoUnits_asInstructor_shouldCreateAttachmentVideoUnits() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        LectureUnitSplitInformationDTO lectureUnitSplitInfo = request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/data/" + filename, HttpStatus.OK,
                LectureUnitSplitInformationDTO.class);

        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);

        lectureUnitSplitInfo = new LectureUnitSplitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), "");

        List<AttachmentVideoUnit> attachmentVideoUnits = request.postListWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/split/" + filename,
                lectureUnitSplitInfo, AttachmentVideoUnit.class, HttpStatus.OK);

        assertThat(attachmentVideoUnits).hasSize(2);
        assertThat(slideRepository.findAll()).hasSize(20); // 20 slides should be created for 2 attachment video units

        List<Long> attachmentVideoUnitIds = attachmentVideoUnits.stream().map(AttachmentVideoUnit::getId).toList();
        List<AttachmentVideoUnit> attachmentVideoUnitList = attachmentVideoUnitRepository.findAllById(attachmentVideoUnitIds);

        assertThat(attachmentVideoUnitList).hasSize(2);
        assertThat(attachmentVideoUnitList).isEqualTo(attachmentVideoUnits);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentVideoUnits_asInstructor_shouldRemoveSlides() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        LectureUnitSplitInformationDTO lectureUnitSplitInfo = request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/data/" + filename, HttpStatus.OK,
                LectureUnitSplitInformationDTO.class);
        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);

        var commaSeparatedKeyPhrases = String.join(",", new String[] { "Break", "Example solution" });
        lectureUnitSplitInfo = new LectureUnitSplitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), commaSeparatedKeyPhrases);

        List<AttachmentVideoUnit> attachmentVideoUnits = request.postListWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/split/" + filename,
                lectureUnitSplitInfo, AttachmentVideoUnit.class, HttpStatus.OK);
        assertThat(attachmentVideoUnits).hasSize(2);
        assertThat(slideRepository.findAll()).hasSize(18); // 18 slides should be created for 2 attachment video units (1 break slide is removed and 1 solution slide is removed)

        List<Long> attachmentVideoUnitIds = attachmentVideoUnits.stream().map(AttachmentVideoUnit::getId).toList();
        List<AttachmentVideoUnit> attachmentVideoUnitList = attachmentVideoUnitRepository.findAllById(attachmentVideoUnitIds);

        assertThat(attachmentVideoUnitList).hasSize(2);
        assertThat(attachmentVideoUnitList).isEqualTo(attachmentVideoUnits);

        // first unit
        String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, attachmentVideoUnitList.getFirst().getAttachment().getLink());
        byte[] fileBytesFirst = request.get(requestUrl, HttpStatus.OK, byte[].class);

        try (PDDocument document = Loader.loadPDF(fileBytesFirst)) {
            // 5 is the number of pages for the first unit (after break and solution are removed)
            assertThat(document.getNumberOfPages()).isEqualTo(5);
        }

        // second unit
        String attachmentPathSecondUnit = attachmentVideoUnitList.get(1).getAttachment().getLink();
        String attachmentRequestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, attachmentPathSecondUnit);
        byte[] fileBytesSecond = request.get(attachmentRequestUrl, HttpStatus.OK, byte[].class);

        try (PDDocument document = Loader.loadPDF(fileBytesSecond)) {
            // 13 is the number of pages for the second unit
            assertThat(document.getNumberOfPages()).isEqualTo(13);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentVideoUnits_asInstructor_shouldThrowError() throws Exception {
        var lectureFile = createLectureFile(false);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        request.postListWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/split/" + filename, lectureUnitSplits, AttachmentVideoUnit.class,
                HttpStatus.BAD_REQUEST);
        request.postListWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/split/non-existent-file", lectureUnitSplits,
                AttachmentVideoUnit.class, HttpStatus.NOT_FOUND);
    }

    private void testAllPreAuthorize() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "");

        request.postWithMultipartFile("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/upload", null, "upload", createLectureFile(true), String.class,
                HttpStatus.FORBIDDEN);
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/data/any-file", HttpStatus.FORBIDDEN, LectureUnitSplitInformationDTO.class);
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/slides-to-remove/any-file", HttpStatus.FORBIDDEN, LectureUnitSplitInformationDTO.class,
                params);
        request.postListWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/split/any-file", lectureUnitSplits, AttachmentVideoUnit.class,
                HttpStatus.FORBIDDEN);
    }

    /**
     * Generates a lecture file with 20 pages and with 2 slides that contain Outline
     *
     * @param shouldBePDF true if the file should be PDF, false if it should be word doc
     * @return MockMultipartFile lecture file
     */
    private MockMultipartFile createLectureFile(boolean shouldBePDF) throws IOException {
        var font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = new PDDocument()) {
            if (shouldBePDF) {
                for (int i = 1; i <= 20; i++) {
                    document.addPage(new PDPage());
                    PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(i - 1));

                    switch (i) {
                        case 6 -> generateBreakSlide(contentStream, font);
                        case 7 -> generateOutlineSlide(contentStream, font, "Example solution");
                        case 2, 8 -> generateOutlineSlide(contentStream, font, "Outline");
                        default -> generateContentSlide(contentStream, font);
                    }
                }
                document.save(outputStream);
                document.close();
                return new MockMultipartFile("file", "lectureFile.pdf", "application/json", outputStream.toByteArray());
            }
            return new MockMultipartFile("file", "lectureFileWord.doc", "application/msword", outputStream.toByteArray());
        }
    }

    private void generateBreakSlide(PDPageContentStream contentStream, PDType1Font font) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, 12);
        contentStream.newLineAtOffset(25, -15);
        contentStream.showText("itp20..");
        contentStream.newLineAtOffset(25, 500);
        contentStream.showText("Break");
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Have fun");
        contentStream.endText();
        contentStream.close();
    }

    private void generateOutlineSlide(PDPageContentStream contentStream, PDType1Font font, String header) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, 12);
        contentStream.newLineAtOffset(25, -15);
        contentStream.showText("itp20..");
        contentStream.newLineAtOffset(25, 500);
        contentStream.showText(header);
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("First Unit");
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Second Unit");
        contentStream.endText();
        contentStream.close();
    }

    private void generateContentSlide(PDPageContentStream contentStream, PDType1Font font) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, 12);
        contentStream.newLineAtOffset(25, 500);
        String text = "This is the sample document";
        contentStream.showText(text);
        contentStream.endText();
        contentStream.close();
    }

    private MockMultipartFile readFromFile() throws IOException {
        var file = ResourceUtils.getFile("classpath:test-data/attachment/Infun.pdf");
        try (var inputStream = Files.newInputStream(file.toPath())) {
            return new MockMultipartFile("file", "lectureFile.pdf", "application/json", inputStream.readAllBytes());
        }
    }

    /**
     * Uploads a lecture file. Needed to test some errors (wrong filetype) and to keep test cases independent.
     *
     * @param file the file to be uploaded
     * @return String filename in the temp folder
     */
    private String manualFileUpload(long lectureId, MockMultipartFile file) throws IOException {
        return lectureUnitProcessingService.saveTempFileForProcessing(lectureId, file, 10);
    }
}
