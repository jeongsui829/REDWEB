package com.scuredweb.recheck;
import com.scuredweb.file.controller.*;
import com.scuredweb.file.dao.FileDAO;
import com.scuredweb.file.dto.AttachmentDTO;
import com.scuredweb.common.util.UploadPathUtil;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import static org.mockito.Mockito.*;
class FinalFileTest {
    @ParameterizedTest @CsvSource({"download,PUBLIC","download,HIDDEN","image,PUBLIC","image,HIDDEN"})
    void privateAttachmentsAreNotCachedButPublicPolicyRemains(String route,String status,@TempDir Path root) throws Exception {
        Files.write(root.resolve("fixture.png"),new byte[]{1,2,3});
        AttachmentDTO a=new AttachmentDTO();a.setPostStatus(status);a.setUploaderId(1L);a.setSavedName("fixture.png");a.setOriginalName("fixture.png");a.setFileExt(".png");a.setImage(true);
        HttpServletRequest r=mock(HttpServletRequest.class);HttpServletResponse p=mock(HttpServletResponse.class);HttpSession session=mock(HttpSession.class);
        when(r.getServletPath()).thenReturn("/files/"+route);when(r.getParameter("savedName")).thenReturn("fixture.png");when(r.getSession(false)).thenReturn(session);
        UserDTO user=new UserDTO();user.setUserId(1L);when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user);when(p.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
        try(var dirs=mockStatic(UploadPathUtil.class);var daos=mockConstruction(FileDAO.class,(d,c)->when(d.findAttachmentBySavedName("fixture.png")).thenReturn(a))) {
            dirs.when(UploadPathUtil::getAttachmentsDirectory).thenReturn(root);
            HttpServlet controller=route.equals("download")?new FileDownloadController():new FileImageController();
            var method=controller.getClass().getDeclaredMethod("doGet",HttpServletRequest.class,HttpServletResponse.class);method.setAccessible(true);method.invoke(controller,r,p);
            if(status.equals("HIDDEN")) {verify(p).setHeader("Cache-Control","private, no-store");verify(p).setHeader("Pragma","no-cache");verify(p).setHeader("Referrer-Policy","no-referrer");}
            else verify(p,never()).setHeader(eq("Cache-Control"),contains("no-store"));
            verify(p).setContentLengthLong(3L);
        }
    }
}
