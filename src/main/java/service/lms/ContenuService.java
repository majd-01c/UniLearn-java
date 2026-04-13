package service.lms;

import entities.Contenu;
import repository.lms.ContenuRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class ContenuService {

    private final ContenuRepository contenuRepo = new ContenuRepository();

    public List<Contenu> listAll() { return contenuRepo.findAll(); }
    public List<dto.lms.ContenuOptionDto> listAllOptionsDto() {
        return contenuRepo.findAll().stream()
                .map(c -> new dto.lms.ContenuOptionDto(c.getId(), c.getTitle()))
                .collect(java.util.stream.Collectors.toList());
    }
    public Optional<Contenu> findById(Integer id) { return contenuRepo.findById(id); }
    public List<Contenu> findByType(String type) { return contenuRepo.findByType(type); }
    public long count() { return contenuRepo.count(); }

    public Contenu createContenu(String title, String type, boolean published,
                                  String fileName, String fileType, Integer fileSize) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        Contenu c = new Contenu();
        c.setTitle(title.trim());
        c.setType(type);
        c.setPublished(published ? (byte) 1 : (byte) 0);
        c.setFileName(fileName);
        c.setFileType(fileType);
        c.setFileSize(fileSize);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return contenuRepo.save(c);
    }

    public Contenu updateContenu(Integer id, String title, String type, boolean published,
                                  String fileName, String fileType, Integer fileSize) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        Contenu c = contenuRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Content not found: " + id));
        c.setTitle(title.trim());
        c.setType(type);
        c.setPublished(published ? (byte) 1 : (byte) 0);
        if (fileName != null) c.setFileName(fileName);
        if (fileType != null) c.setFileType(fileType);
        if (fileSize != null) c.setFileSize(fileSize);
        c.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return contenuRepo.update(c);
    }

    public void deleteContenu(Integer id) { util.RoleGuard.requireCurrentAdminOrTeacher(); contenuRepo.delete(id); }
}
