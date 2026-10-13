import struct,unittest
from rowe_static import extraction,half,inspect,load_instruction,shift_instruction


class StaticTest(unittest.TestCase):
    def test_invented_thumb_field(self):
        # Invented Thumb instructions: LDR r0,[r4,#20]; LSL r0,#1; LSR r2,r0,#11.
        rom=struct.pack('<3H',0x6800|(5<<6)|(4<<3), (1<<6),0x0800|(11<<6)|2)
        self.assertEqual(extraction(rom,0x08000000),dict(offset=20,storage_bytes=4,bit=10,width=21,instruction_address='0x08000000'))
        self.assertEqual(load_instruction(0x7800|(7<<6)|(3<<3)|1),dict(bytes=1,offset=7,base=3,destination=1))
        self.assertEqual(load_instruction(0x8800|(3<<6)),dict(bytes=2,offset=6,base=0,destination=0))
        for bad in [b'',rom[:5],rom[:2]+struct.pack('<H',0x2000)+rom[4:],rom[:4]+struct.pack('<H',0x0800|2)]:
            with self.assertRaises(ValueError):extraction(bad,0x08000000)
        for address in [0x07ffffff,0x08000001,0x08000006]:
            with self.assertRaises(ValueError):half(rom,address)
        with self.assertRaises(ValueError):load_instruction(0x7000)
        with self.assertRaises(ValueError):shift_instruction(0x1000)
        with self.assertRaises(ValueError):inspect(rom)


if __name__=='__main__':unittest.main()
