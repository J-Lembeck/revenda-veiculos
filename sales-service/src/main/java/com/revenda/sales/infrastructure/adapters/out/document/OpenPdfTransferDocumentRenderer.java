package com.revenda.sales.infrastructure.adapters.out.document;

import com.revenda.sales.domain.model.Payer;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.TransferBuyer;
import com.revenda.sales.domain.model.TransferDocument;
import com.revenda.sales.domain.model.TransferVehicle;
import com.revenda.sales.domain.ports.out.RenderTransferDocumentPort;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * O documento de transferência num PDF de uma página, com as fontes padrão do PDF (nada embutido): o vendedor, o
 * comprador, o veículo e a venda, nos campos do ATPV-e, e o aviso de que não substitui o documento do Detran. Datas no
 * fuso de {@code revenda.documents.zone}; dinheiro em reais.
 */
@Component
@RequiredArgsConstructor
class OpenPdfTransferDocumentRenderer implements RenderTransferDocumentPort {

    static final String SELLER = "REVENDA SIMULADA";
    static final String NOTICE = "Documento simplificado com os campos da Autorização para Transferência de "
            + "Propriedade de Veículo (ATPV-e). Não substitui o documento emitido pelo Detran.";

    private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

    private final DocumentsProperties properties;

    @Override
    public byte[] render(TransferDocument transfer) {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(document, pdf);
        document.addTitle("Documento de transferência de veículo");
        document.addCreator(SELLER);
        document.open();
        Paragraph title = new Paragraph("Documento de transferência de veículo", TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        Paragraph notice = new Paragraph(NOTICE, SMALL);
        notice.setAlignment(Element.ALIGN_CENTER);
        notice.setSpacingAfter(12);
        document.add(notice);
        document.add(section("Vendedor", new String[][] {{"Nome", SELLER}}));
        document.add(section("Comprador", buyer(transfer.buyer())));
        document.add(section("Veículo", vehicle(transfer.vehicle())));
        document.add(section("Venda", sale(transfer.purchase(), transfer.issuedAt())));
        document.close();
        return pdf.toByteArray();
    }

    private String[][] buyer(TransferBuyer buyer) {
        return new String[][] {
                {"Nome", buyer.fullName()},
                {"CPF", formatCpf(buyer.cpf())},
                {"E-mail", buyer.email()},
                {"Endereço", address(buyer.address())}};
    }

    private static String[][] vehicle(TransferVehicle vehicle) {
        String name = vehicle.brand() + " " + vehicle.model()
                + (vehicle.trimName() == null || vehicle.trimName().isBlank() ? "" : " " + vehicle.trimName());
        return new String[][] {
                {"Marca / modelo / versão", name},
                {"Ano de fabricação / modelo", vehicle.manufactureYear() + " / " + vehicle.modelYear()},
                {"Cor", vehicle.color()},
                {"Quilometragem", NumberFormat.getIntegerInstance(BRAZIL).format(vehicle.mileageKm()) + " km"},
                {"Placa", vehicle.plate()},
                {"Chassi", vehicle.vin()},
                {"RENAVAM", vehicle.renavam()}};
    }

    private String[][] sale(Purchase purchase, Instant issuedAt) {
        return new String[][] {
                {"Valor da venda", NumberFormat.getCurrencyInstance(BRAZIL).format(purchase.price().amount())},
                {"Pagamento confirmado em", dateTime(purchase.paidAt())},
                {"Retirada em", dateTime(purchase.completedAt())},
                {"Compra", purchase.id().toString()},
                {"Emitido em", dateTime(issuedAt)}};
    }

    private static PdfPTable section(String name, String[][] rows) {
        PdfPTable table = new PdfPTable(new float[] {1.3f, 3f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        PdfPCell header = new PdfPCell(new Phrase(name, SECTION));
        header.setColspan(2);
        header.setBorder(PdfPCell.BOTTOM);
        header.setPaddingBottom(4);
        table.addCell(header);
        for (String[] row : rows) {
            table.addCell(cell(row[0], LABEL));
            table.addCell(cell(row[1], VALUE));
        }
        return table;
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(3);
        return cell;
    }

    private static String address(Payer.Address address) {
        String complement = address.complement() == null || address.complement().isBlank()
                ? "" : ", " + address.complement();
        return address.street() + ", " + address.number() + complement + " — " + address.district() + ", "
                + address.city() + "/" + address.state() + " — CEP " + formatZipCode(address.zipCode());
    }

    private String dateTime(Instant instant) {
        return instant == null ? "" : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", BRAZIL)
                .withZone(properties.zone()).format(instant);
    }

    static String formatCpf(String digits) {
        if (digits == null || !digits.matches("\\d{11}")) {
            return digits;
        }
        return digits.substring(0, 3) + "." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-"
                + digits.substring(9);
    }

    private static String formatZipCode(String digits) {
        return digits != null && digits.matches("\\d{8}") ? digits.substring(0, 5) + "-" + digits.substring(5) : digits;
    }
}
