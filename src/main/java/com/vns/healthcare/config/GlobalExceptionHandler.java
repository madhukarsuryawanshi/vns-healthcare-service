package com.vns.healthcare.config;

import com.vns.healthcare.exception.BusinessException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUpload(MaxUploadSizeExceededException ex, Model model) {
        model.addAttribute("error", "File is too large. Maximum size is 10 MB.");
        return "error";
    }
}
