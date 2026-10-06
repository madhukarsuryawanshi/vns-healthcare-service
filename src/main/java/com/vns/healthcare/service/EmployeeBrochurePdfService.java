package com.vns.healthcare.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.vns.healthcare.entity.Employee;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class EmployeeBrochurePdfService {

    private static final List<String> SKILL_ORDER = Arrays.asList(
            "interpersonal-relationship",
            "sponge-bath",
            "bed-pan-diaper",
            "mouth-feeding",
            "ryles-tube-feeding",
            "blood-pressure",
            "suction-mouth",
            "trachea",
            "tracheostomy-care",
            "blood-sugar-test",
            "medications-oral",
            "medications-intravenous",
            "medications-intramuscular",
            "medications-subcutaneous",
            "catheterisation-care-only-care",
            "catheterisation-care-expert"
    );

    private static final Map<String, String> SKILL_LABELS = new java.util.LinkedHashMap<String, String>();

    static {
        SKILL_LABELS.put("interpersonal-relationship", "Interpersonal Relationship");
        SKILL_LABELS.put("sponge-bath", "Sponge Bath");
        SKILL_LABELS.put("bed-pan-diaper", "Bed Pan / Diaper");
        SKILL_LABELS.put("mouth-feeding", "Mouth Feeding");
        SKILL_LABELS.put("ryles-tube-feeding", "Ryle's Tube Feeding");
        SKILL_LABELS.put("blood-pressure", "Vitals: Blood Pressure");
        SKILL_LABELS.put("suction-mouth", "Suction: Mouth");
        SKILL_LABELS.put("trachea", "Trachea");
        SKILL_LABELS.put("tracheostomy-care", "Tracheostomy Care");
        SKILL_LABELS.put("blood-sugar-test", "Blood Sugar Test (BSL)");
        SKILL_LABELS.put("medications-oral", "Medications: Oral");
        SKILL_LABELS.put("medications-intravenous", "Medications: Intravenous");
        SKILL_LABELS.put("medications-intramuscular", "Medications: Intramuscular");
        SKILL_LABELS.put("medications-subcutaneous", "Medications: Subcutaneous");
        SKILL_LABELS.put("catheterisation-care-only-care", "Catheterisation & Care (Only Care)");
        SKILL_LABELS.put("catheterisation-care-expert", "Catheterisation & Care (Expert)");
    }

    public byte[] generate(Employee employee, List<String> brochureOptions, Integer employeeAge) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, buffer);
            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 22, Font.BOLD, BaseColor.DARK_GRAY);
            Font headingFont = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD, BaseColor.DARK_GRAY);
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK);
            Font smallFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.DARK_GRAY);

            document.add(new Paragraph("VNS Healthcare", titleFont));
            document.add(new Paragraph("A Home Healthcare Company", smallFont));
            document.add(new Paragraph(" ", normalFont));
            document.add(new Paragraph("EMPLOYEE PROFILE", headingFont));
            document.add(new Paragraph(" ", normalFont));

            PdfPTable profile = new PdfPTable(2);
            profile.setWidthPercentage(100);
            profile.setSpacingBefore(8f);
            profile.setWidths(new float[]{3f, 1.3f});
            profile.setKeepTogether(true);

            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(PdfPCell.NO_BORDER);
            leftCell.addElement(new Paragraph(employee.getFullName() == null ? "Employee" : employee.getFullName(), new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.BLACK)));
            if (employee.getGender() != null) {
                leftCell.addElement(new Paragraph("Gender: " + employee.getGender().name(), normalFont));
            }
            if (employee.getMaritalStatus() != null && !employee.getMaritalStatus().trim().isEmpty()) {
                leftCell.addElement(new Paragraph("Marital status: " + employee.getMaritalStatus(), normalFont));
            }
            if (employeeAge != null) {
                leftCell.addElement(new Paragraph("Age: " + employeeAge, normalFont));
            }
            if (employee.getEmpCode() != null) {
                leftCell.addElement(new Paragraph("Employee code: " + employee.getEmpCode(), normalFont));
            }
            if (employee.getKnownLanguages() != null && !employee.getKnownLanguages().isEmpty()) {
                leftCell.addElement(new Paragraph("Languages: " + String.join(", ", employee.getKnownLanguages()), normalFont));
            }
            if (employee.getNoOfExperience() != null) {
                leftCell.addElement(new Paragraph("Experience: " + employee.getNoOfExperience() + " yrs", normalFont));
            }
            if (employee.getFullAddress() != null) {
                leftCell.addElement(new Paragraph("Address: " + employee.getFullAddress(), normalFont));
            }

            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(PdfPCell.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            rightCell.setPaddingTop(10f);
            rightCell.setFixedHeight(120f);
            rightCell.setBackgroundColor(new BaseColor(247, 240, 228));
            rightCell.addElement(new Paragraph("Profile photo", smallFont));
            rightCell.addElement(new Paragraph("Not available", smallFont));

            profile.addCell(leftCell);
            profile.addCell(rightCell);
            document.add(profile);

            document.add(new Paragraph(" ", normalFont));
            document.add(new Paragraph("Basic Skills", headingFont));

            PdfPTable skills = new PdfPTable(2);
            skills.setWidthPercentage(100);
            skills.setSpacingBefore(8f);
            skills.setSpacingAfter(16f);
            skills.setWidths(new float[]{1f, 5f});

            List<String> selected = brochureOptions == null ? new ArrayList<String>() : brochureOptions;
            for (String key : SKILL_ORDER) {
                boolean isSelected = selected.contains(key);
                PdfPCell checkCell = new PdfPCell(new Phrase(isSelected ? "✓" : "✕", new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, isSelected ? BaseColor.GREEN : BaseColor.GRAY)));
                checkCell.setBorder(PdfPCell.NO_BORDER);
                checkCell.setPaddingTop(4f);
                checkCell.setPaddingBottom(4f);
                checkCell.setHorizontalAlignment(Element.ALIGN_CENTER);

                PdfPCell labelCell = new PdfPCell(new Phrase(SKILL_LABELS.get(key), normalFont));
                labelCell.setBorder(PdfPCell.NO_BORDER);
                labelCell.setPaddingTop(4f);
                labelCell.setPaddingBottom(4f);

                skills.addCell(checkCell);
                skills.addCell(labelCell);
            }
            document.add(skills);

            document.add(new Paragraph("This information and knowledge of employee has taken after interview, training and documents are provided from the employee to the company. This letter does not require any other documents.", smallFont));
            document.add(new Paragraph(" ", normalFont));
            document.add(new Paragraph("Prepared for VNS Healthcare", normalFont));
            document.add(new Paragraph("Authorized Signatory", smallFont));

            document.close();
            return buffer.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Unable to generate employee brochure PDF", e);
        }
    }
}
