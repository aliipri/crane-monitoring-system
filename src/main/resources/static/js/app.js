document.querySelectorAll(".file-field input[type='file']").forEach((input) => {
    const label = input.closest(".file-field")?.querySelector(".file-field__name");
    if (!label) {
        return;
    }

    input.addEventListener("change", () => {
        const fileName = input.files?.[0]?.name;
        label.textContent = fileName || label.dataset.defaultLabel || "Выбрать TXT";
        label.title = fileName || "";
    });
});
